package cc.kasumi.uhc.player;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.mongo.MCollection;
import lombok.Getter;
import lombok.Setter;
import org.bson.Document;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
public class UHCPlayer {

    private final UUID uuid;
    private int kills, deaths;

    @Setter
    private UHCPlayerStatus status;

    public UHCPlayer(UUID uuid) {
        this.uuid = uuid;
        this.status = UHCPlayerStatus.LOBBY;
    }

    public CompletableFuture<UHCPlayer> load() {
        return CompletableFuture.supplyAsync(() -> {
            MCollection mCollection = UHC.getInstance().getPlayerCollection();
            Document document = mCollection.getDocument(getKey());

            if (document != null) {
                kills = document.getInteger("kills");
                deaths = document.getInteger("deaths");
            }

            return UHCPlayer.this;
        });
    }

    public void save() {
        CompletableFuture.runAsync(() -> {
           MCollection mCollection = UHC.getInstance().getPlayerCollection();

           mCollection.updateDocument(getKey(), getPlayerDocument());
        });
    }

    public Document getKey() {
        return new Document("uuid", uuid);
    }

    public Document getPlayerDocument() {
        return getKey()
                .append("kills", kills)
                .append("deaths", deaths);
    }
}