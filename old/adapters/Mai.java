package org.immutables.mongo.fixture.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fakemongo.Fongo;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoDatabase;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.immutables.mongo.bson4gson.GsonCodecs;
import org.immutables.mongo.bson4jackson.JacksonCodecs;
import org.immutables.mongo.repository.RepositorySetup;
import org.immutables.mongo.types.Id;
import org.immutables.mongo.types.TypeAdapters;

public class Mai {
  public static void main22(String... args) throws IOException {
    ObjectMapper mapper = new ObjectMapper();

    Props p = new Props();
    p.put("a", 1);
    p.put("b", 2);

    System.out.println(p);
    mapper.writeValue(System.out, p);
  }

  public static void main(String... args) throws IOException {
    ObjectMapper mapper = new ObjectMapper();

    CodecRegistry jacksonCodecs = JacksonCodecs.registryFromMapper(mapper);

    GsonBuilder gb = new GsonBuilder()
        .registerTypeAdapterFactory(new TypeAdapters()) // just for Id
        .registerTypeAdapterFactory(new GsonAdaptersSampl()) // for our top document
        .registerTypeAdapterFactory(GsonCodecs.delegatingTypeAdapterFactory(jacksonCodecs));

    Gson gson = gb.create();
    CodecRegistry codecRegistry = CodecRegistries.fromRegistries(
        GsonCodecs.codecRegistryFromGson(gson),
        jacksonCodecs); // probably as backup adding it also here

    MongoDatabase database = new Fongo("fake").getDatabase("db");
    RepositorySetup setup = RepositorySetup.builder()
        .database(database)
        .gson(gson)
        .executor(MoreExecutors.listeningDecorator(Executors.newCachedThreadPool()))
        .codecRegistry(codecRegistry)
        .build();

    SamplRepository sm = new SamplRepository(setup);

    Props p = new Props();
    p.put("a", 1);
    p.put("b", 2);

    sm.insert(ImmutableSampl.builder()
        .id(Id.generate())
        .props(p)
        .build())
        .getUnchecked();

    List<Sampl> samps = sm.findAll().fetchAll().getUnchecked();


    System.out.println(samps);

    for (Document document : database.getCollection("sampl").find()) {
      System.out.println(document.toJson());
    }
  }
}
