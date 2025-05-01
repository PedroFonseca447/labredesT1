
import java.net.InetAddress;

public class DeviceInfo {
    String nome;
    InetAddress ip;
    int porta;
    long timestamp;

    public DeviceInfo( String nome,InetAddress ip , int porta, long timestamp) {
        this.ip = ip;
        this.nome = nome;
        this.porta = porta;
        this.timestamp = timestamp;
    }


}
