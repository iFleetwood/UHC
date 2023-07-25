package cc.kasumi.uhc.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import lombok.Getter;
import org.bson.Document;

@Getter
public class MCollection {

    private final MongoCollection<Document> collection;
    private final MongoDatabase database;

    public MCollection(MDatabase database, String collection) {
        MongoDatabase mongoDatabase = database.getDatabase();

        this.database = mongoDatabase;
        this.collection = mongoDatabase.getCollection(collection);
    }

    public Document getDocument(Document key) {
        return collection.find(key).limit(1).first();
    }

    public void updateDocument(Document key, Document updatedDocument) {
        collection.updateOne(key, updatedDocument, new UpdateOptions().upsert(true));
    }
}
