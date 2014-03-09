package ru.kulikovd.prismaticfeed

import java.lang.reflect.{ParameterizedType, Type}

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.core.JsonParser.Feature
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.module.scala.DefaultScalaModule


trait JsonSupport {

  val jsonMapper  = JsonSupport.mapper
  val jsonFactory = JsonSupport.factory

  def serialize(value: Any): String =
    jsonMapper.writeValueAsString(value)

  def deserialize[T: Manifest](value: String): T =
    jsonMapper.readValue(value, typeReference[T])

  def deserialize[T: Manifest](value: Array[Byte]): T =
    deserialize[T](new String(value))

  def deserialize[T: Manifest](value: String, clazz: Class[T]): T =
    deserialize(value)

  def deserialize[T: Manifest](value: Array[Byte], clazz: Class[T]): T =
    deserialize(value)

  private def typeReference[T: Manifest] = new TypeReference[T] {
    override def getType = typeFromManifest(manifest[T])
  }

  private def typeFromManifest(m: Manifest[_]): Type = {
    if (m.typeArguments.isEmpty) { m.runtimeClass }
    else new ParameterizedType {
      def getRawType = m.runtimeClass
      def getActualTypeArguments = m.typeArguments.map(typeFromManifest).toArray
      def getOwnerType = null
    }
  }
}


object JsonSupport {

  val mapper = new ObjectMapper
  mapper.registerModule(DefaultScalaModule)
  mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  mapper.configure(Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true)
  mapper.configure(Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
  mapper.configure(Feature.ALLOW_SINGLE_QUOTES, true)
  mapper.configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, false)
  mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false)

  val factory = new MappingJsonFactory(mapper)
}
