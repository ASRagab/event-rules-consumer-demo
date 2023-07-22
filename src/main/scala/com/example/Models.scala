package com.example

import zio._
import zio.json._
import zio.kafka.serde._

object Models {
  case class Rule(id: Long, value: String)

  case class Event(tenantId: String, value: String)

  object Event {
    implicit val decoder: JsonDecoder[Event] = DeriveJsonDecoder.gen[Event]
    implicit val encoder: JsonEncoder[Event] = DeriveJsonEncoder.gen[Event]

    implicit val serde: Serde[Any, Event] =
      Serde.string.inmapM[Any, Event](str => ZIO.fromEither(str.fromJson[Event]).mapError(new RuntimeException(_)))(
        rule => ZIO.succeed(rule.toJson)
      )
  }

  object Rule {
    implicit val decoder: JsonDecoder[Rule] = DeriveJsonDecoder.gen[Rule]
    implicit val encoder: JsonEncoder[Rule] = DeriveJsonEncoder.gen[Rule]

    implicit val serde: Serde[Any, Rule] =
      Serde.string.inmapM[Any, Rule](str => ZIO.fromEither(str.fromJson[Rule]).mapError(new RuntimeException(_)))(
        rule => ZIO.succeed(rule.toJson)
      )
  }

}
