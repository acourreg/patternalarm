package com.patternalarm.flinkprocessor.serialization

import com.esotericsoftware.kryo.{Kryo, Serializer}
import com.esotericsoftware.kryo.io.{Input, Output}
import java.time.Instant

/**
 * Kryo serializer for java.time.Instant
 * Fixes Java 9+ module access issues
 */
class InstantSerializer extends Serializer[Instant] {

  override def write(kryo: Kryo, output: Output, instant: Instant): Unit = {
    output.writeLong(instant.getEpochSecond)
    output.writeInt(instant.getNano)
  }

  override def read(kryo: Kryo, input: Input, `type`: Class[Instant]): Instant = {
    val seconds = input.readLong()
    val nanos = input.readInt()
    Instant.ofEpochSecond(seconds, nanos.toLong)
  }
}
