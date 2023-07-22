package com.example

sealed abstract class Topic(val name: String)

object Topic {
  case object Rules  extends Topic("rules")
  case object Events extends Topic("events")
}
