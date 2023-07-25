package cc.kasumi.uhc.mongo;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoDatabase;
import lombok.Getter;

@Getter
public class MDatabase {

    private MongoDatabase database;

    public MDatabase(String uri, String database) {
        MongoClient mongoClient = new MongoClient(new MongoClientURI(uri));
        this.database = mongoClient.getDatabase(database);
    }
}
