package com.example

import com.example.Models._
import org.apache.kafka.clients.producer.RecordMetadata
import zio._
import zio.kafka.consumer._
import zio.kafka.producer._
import zio.kafka.serde._
import zio.logging.backend.SLF4J
import zio.stream._

import scala.util.Try

object QueueProcessing extends ZIOAppDefault {
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  /**
   * Generic producer which will produce a value to a topic
   * @param topic Topic
   * @param random Randon integer will server as the key
   * @param value Value to be produced
   * @param serde The serde for the value
   * @tparam T Type of the value
   * @return
   */
  private def genericProducer[T](topic: Topic, random: Int, value: T)(
    implicit serde: Serde[Any, T]
  ): RIO[Producer, RecordMetadata] =
    Producer.produce[Any, Long, T](
      topic = topic.name,
      key = random % 4,
      value = value,
      keySerializer = Serde.long,
      valueSerializer = serde
    )

  /**
   * Producer stream which will generate rules or events on a schedule based on the topic for which it's producing
   * @param topic Topic
   * @param duration Duration
   * @return
   */
  private def producer(topic: Topic, duration: Duration): ZStream[Producer, Throwable, Nothing] =
    ZStream
      .repeatZIO(Random.nextIntBetween(0, 10000))
      .schedule(Schedule.spaced(duration).jittered)
      .mapZIO { random =>
        topic match {
          case Topic.Rules  => genericProducer(topic, random, Rule(random.toLong, random.toString))
          case Topic.Events => genericProducer(topic, random, Event(random.toString, random.toString))
        }
      }
      .drain

  /**
   * Most of the interesting work happens here, we consume events and then process them with the rules in the map
   * We also update the map with the latest rule if one is available. And then we process the event with the map.
   * @param topic Topic
   * @param queue Queue[Rule]
   * @param ruleMap Map[Long, Rule]
   * @return
   */
  private def rulesConsumer(topic: Topic, queue: Queue[Rule]): ZStream[Consumer, Throwable, Nothing] =
    Consumer
      .plainStream(Subscription.topics(topic.name), Serde.long, Rule.serde.asTry)
      .tap(messageTry => messageTry.value.fold(ex => ZIO.log(ex.getMessage), queue.offer(_)))
      .map(_.offset)
      .aggregateAsync(Consumer.offsetBatches)
      .mapZIO(_.commit)
      .drain

  /**
   * Consumer stream which will consume rules and offer them to the queue "side effectfully"
   *
   * @param topic Topic
   * @param queue Queue[Rule]
   * @return
   */
  private def eventConsumer(
    topic: Topic,
    queue: Queue[Rule],
    ruleMap: Ref[Map[Long, Rule]]
  ): ZStream[Consumer, Throwable, Nothing] =
    Consumer
      .plainStream(Subscription.topics(topic.name), Serde.long, Event.serde.asTry)
      .tap { messageTry =>
        for {
          maybeRule <- queue.poll
          map <- maybeRule.fold(ruleMap.get)(rule =>
                  ruleMap.updateAndGet(_.updated(rule.id, rule)) <* ZIO.log(s"Rule ${rule} added to map")
                )
          _ <- ZIO.foreachDiscard(processEvent(messageTry.value, map))(ZIO.log(_))
        } yield ()
      }
      .map(_.offset)
      .aggregateAsync(Consumer.offsetBatches)
      .mapZIO(_.commit)
      .drain

  /**
   * Obviously this is a very simple processing function, but it's just to show how we can process events with rules
   * @param eventTry Try[Event]
   * @param rules Map[Long, Rule]
   * @return
   */
  private def processEvent(eventTry: Try[Event], rules: Map[Long, Rule]): Chunk[String] =
    eventTry.map { event =>
      val processCondition: Rule => Boolean =
        rule => Try(Integer.parseInt(rule.value) - Integer.parseInt(event.value) > 1000).getOrElse(false)

      Chunk.fromIterable {
        rules.map {
          case (_, rule) if processCondition(rule) => s"Event ${event.value} processed with Rule ${rule}"
          case _                                   => s"Event ${event.value} no rule applied"
        }
      }
    }.getOrElse(Chunk.empty)

  /**
   * This is the main pipeline that will be executed. We fork the streams so that they can run in concurrently.
   * Note we've artificially introduced latency so we can see logging of events and rules
   * @param queue Queue[Rule]
   * @param ruleMap Ref[Map[Long, Rule]\]
   * @return
   */
  private def pipeline(
    queue: Queue[Rule],
    ruleMap: Ref[Map[Long, Rule]]
  ): ZIO[Consumer with Producer, Throwable, Unit] =
    for {
      ruleFib  <- producer(Topic.Rules, 5.seconds).merge(rulesConsumer(Topic.Rules, queue)).runDrain.fork
      eventFib <- producer(Topic.Events, 500.millis).merge(eventConsumer(Topic.Events, queue, ruleMap)).runDrain.fork
      _        <- ruleFib.await
      _        <- eventFib.await
    } yield ()

  private val program: ZIO[Consumer with Producer, Throwable, Unit] =
    for {
      ruleMap <- Ref.make(Map(1L -> Rule(1L, "1"))) // Just a ref to hold on to the rule map
      queue   <- Queue.bounded[Rule](1000) // Queue to hold the rules from the rulesConsumer
      _       <- pipeline(queue, ruleMap).ensuring(queue.shutdown)
    } yield ()

  private def producerLayer: ZLayer[Any, Throwable, Producer] =
    ZLayer.scoped(
      Producer.make(
        settings = ProducerSettings(List("localhost:29092"))
      )
    )

  private def consumerLayer: ZLayer[Any, Throwable, Consumer] =
    ZLayer.scoped(
      Consumer.make(
        ConsumerSettings(List("localhost:29092")).withGroupId("group-1")
      )
    )

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = program.provide(producerLayer, consumerLayer)
}
