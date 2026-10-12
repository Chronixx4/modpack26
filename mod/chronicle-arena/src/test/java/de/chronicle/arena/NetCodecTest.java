package de.chronicle.arena;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NetCodecTest {
    @Test void partyRoundTripPreservesConsentAndCountdown() {
        var value=new Net.OpenParty("Boss / Gold",List.of("[Bereit] Alice","[Wartet] Bob"),true,false,7,false);
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try { Net.OpenParty.CODEC.encode(buffer,value);assertEquals(value,Net.OpenParty.CODEC.decode(buffer));assertFalse(buffer.isReadable()); }
        finally { buffer.release(); }
    }
    @Test void adminSettingsRoundTripPreservesEveryField() {
        var value=new Net.AdminChange(150,75,200,12,5,false,true);
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try { Net.AdminChange.CODEC.encode(buffer,value);assertEquals(value,Net.AdminChange.CODEC.decode(buffer));assertFalse(buffer.isReadable()); }
        finally { buffer.release(); }
    }
    @Test void oversizedPartyMessageIsRejected() {
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try { buffer.writeUtf("Arena");buffer.writeVarInt(1025);assertThrows(IllegalArgumentException.class,()->Net.OpenParty.CODEC.decode(buffer)); }
        finally { buffer.release(); }
    }
}
