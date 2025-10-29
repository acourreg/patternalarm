package com.patternalarm.flinkprocessor.utils

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, PropertyNamingStrategies}
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule

/**
 * Jackson wrapper for JSON serialization/deserialization
 * Single source of truth for JSON operations
 */
object JsonUtils {

  // Singleton ObjectMapper for Kafka (snake_case)
  private val mapper: ObjectMapper = {
    val m = new ObjectMapper()
    m.registerModule(DefaultScalaModule)
    m.registerModule(new JavaTimeModule())
    m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    m.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
    m.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    m
  }

  // Singleton ObjectMapper for ML API (camelCase)
  private val camelCaseMapper: ObjectMapper = {
    val m = new ObjectMapper()
    m.registerModule(DefaultScalaModule)
    m.registerModule(new JavaTimeModule())
    m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    m.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
    m.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
    m
  }

  /**
   * Deserialize JSON string to case class (snake_case)
   */
  def fromJson[T](json: String)(implicit m: Manifest[T]): T =
    mapper.readValue(json, m.runtimeClass.asInstanceOf[Class[T]])

  /**
   * Serialize case class to JSON string (snake_case)
   */
  def toJson[T](obj: T): String =
    mapper.writeValueAsString(obj)

  /**
   * Serialize case class to JSON string (camelCase for ML API)
   */
  def toJsonCamelCase[T](obj: T): String =
    camelCaseMapper.writeValueAsString(obj)

  /**
   * Get raw ObjectMapper if needed
   */
  def getMapper: ObjectMapper = mapper
}