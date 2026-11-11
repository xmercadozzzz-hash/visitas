package co.edu.ucc.visitaregistro.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Conversión entre objetos Java y JSON (fechas en formato ISO 8601). */
public final class Json {

    public static final Gson GSON = new GsonBuilder()
        .serializeNulls()
        .registerTypeAdapter(LocalDateTime.class,
            (JsonSerializer<LocalDateTime>) (valor, tipo, ctx) -> new JsonPrimitive(valor.toString()))
        .registerTypeAdapter(LocalDateTime.class,
            (JsonDeserializer<LocalDateTime>) (json, tipo, ctx) -> LocalDateTime.parse(json.getAsString()))
        .registerTypeAdapter(LocalDate.class,
            (JsonSerializer<LocalDate>) (valor, tipo, ctx) -> new JsonPrimitive(valor.toString()))
        .registerTypeAdapter(LocalDate.class,
            (JsonDeserializer<LocalDate>) (json, tipo, ctx) -> LocalDate.parse(json.getAsString()))
        .create();

    private Json() {
    }
}
