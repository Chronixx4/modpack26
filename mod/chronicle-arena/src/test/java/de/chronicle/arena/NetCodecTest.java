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
    @Test void invalidTerminalAndSmithListSizesAreRejected(){
        for(int size:new int[]{-1,1025,Integer.MAX_VALUE}){
            var b=new FriendlyByteBuf(Unpooled.buffer());try{b.writeBoolean(false);b.writeVarInt(100);b.writeBoolean(false);b.writeUtf("Arena");b.writeVarInt(size);assertThrows(IllegalArgumentException.class,()->Net.OpenTerminal.CODEC.decode(b));}finally{b.release();}
        }
        var b=new FriendlyByteBuf(Unpooled.buffer());try{b.writeVarInt(10);b.writeVarInt(65);assertThrows(IllegalArgumentException.class,()->Net.OpenSmith.CODEC.decode(b));}finally{b.release();}
    }
    @Test void excessiveOutgoingPartySizeIsRejectedBeforeWritingMembers(){
        var b=new FriendlyByteBuf(Unpooled.buffer());try{assertThrows(IllegalArgumentException.class,()->Net.OpenParty.CODEC.encode(b,new Net.OpenParty("Arena",java.util.Collections.nCopies(1025,"Spieler"),false,false,-1,false)));}finally{b.release();}
    }
    @Test void oversizedPartyMessageIsRejected() {
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try { buffer.writeUtf("Arena");buffer.writeVarInt(1025);assertThrows(IllegalArgumentException.class,()->Net.OpenParty.CODEC.decode(buffer)); }
        finally { buffer.release(); }
    }
}
