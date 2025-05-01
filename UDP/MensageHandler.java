import java.net.*;
public class MensageHandler { // quem vai processar as mensagens recebidas via udp
    
    public static void processar ( String msg, InetAddress address, int port, UdpService server){
        try {
            if(msg.startsWith("HEARTBEAT")){
                String nome = msg.split(" ", 2)[1];//[] é o nome de quem enviou
                server.registerHeartBeat(nome, address, port);//registra o dispositivo
            }
            else if( msg.startsWith("TALK")){
                String[] talkMessage = msg.split(" ",3);
                System.out.println("Recebido" + talkMessage[2]);//o conteudo da msg
                String ack = "ACK" + talkMessage[1];
                server.sendMensage(ack,address,port);//registra o ack de quem enviou
            } else if(msg.startsWith("ACK")){
                String idMsg = msg.split(" ", 2)[1];
                server.receiveAckId(idMsg);//
            }
        } catch (Exception e) {
            System.err.println("Erro ao receber pacote: " + e.getMessage());
        }
    }
}
