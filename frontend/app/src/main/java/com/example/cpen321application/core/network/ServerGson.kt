package com.example.cpen321application.core.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

/*  marks a reply field the server always sends (use @field:Required on the property).
    Gson ignores Kotlin's null safety: a missing field is left null even when its type
    says it can't be, which would crash later where the field is used. serverGson
    rejects such a reply instead, as a JsonParseException the requests already handle
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Required

// fails the parse when a @Required field of the object just read is missing or null
private object RequiredFieldsFactory : TypeAdapterFactory {
    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        val requiredFields = type.rawType.declaredFields.filter { it.isAnnotationPresent(Required::class.java) }
        if (requiredFields.isEmpty()) return null
        requiredFields.forEach { it.isAccessible = true }
        val delegate = gson.getDelegateAdapter(this, type)

        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T?) = delegate.write(out, value)

            override fun read(reader: JsonReader): T? {
                val value = delegate.read(reader) ?: return null
                val missing = requiredFields.firstOrNull { it.get(value) == null }
                if (missing != null) {
                    throw JsonParseException("${type.rawType.simpleName} is missing ${missing.name}")
                }
                return value
            }
        }
    }
}

// the Gson for every server reply, it rejects replies missing a @Required field
val serverGson: Gson = GsonBuilder()
    .registerTypeAdapterFactory(RequiredFieldsFactory)
    .create()
