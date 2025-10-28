package com.patternalarm.flinkprocessor.utils

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, PropertyNamingStrategies}
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule

/**
 * Jackson wrapper for JSON serialization/deserialization
 * Single source of truth for JSON operations
 */
object JsonUtils {

  // Singleton ObjectMapper (thread-safe, reusable)
  private val mapper: ObjectMapper = {
    val m = new ObjectMapper()
    m.registerModule(DefaultScalaModule)
    m.registerModule(new JavaTimeModule())
    m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false) // Ignore extra fields
    m.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
    m.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    m
  }

  /**
   * Deserialize JSON string to case class
   */
  def fromJson[T](json: String)(implicit m: Manifest[T]): T =
    mapper.readValue(json, m.runtimeClass.asInstanceOf[Class[T]])

  /**
   * Serialize case class to JSON string
   */
  def toJson[T](obj: T): String =
    mapper.writeValueAsString(obj)

  /**
   * Get raw ObjectMapper if needed
   */
  def getMapper: ObjectMapper = mapper
}
