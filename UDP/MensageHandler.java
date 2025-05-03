import java.net.*;
import java.util.Base64;
public class MensageHandler { // quem vai processar as mensagens recebidas via udp
    
    public static void processar ( String msg, InetAddress address, int port, UdpServer server){
        try {
            if(msg.startsWith("HEARTBEAT")){
                String nome = msg.split(" ", 2)[1];//[] é o nome de quem enviou
                server.registerHeartBeat(nome, address, port);//registra o dispositivo
            }
            else if( msg.startsWith("TALK")){
                String[] talkMessage = msg.split(" ",3);
                System.out.println("Recebido " + talkMessage[2]);//o conteudo da msg
                String ack = "ACK" + talkMessage[1];
                server.sendMessage(ack,address,port);//registra o ack de quem enviou
            } else if(msg.startsWith("ACK")){
                String idMsg = msg.split(" ", 2)[1];
                server.receiveAckId(idMsg);//
            } else if (msg.startsWith("FILE")) {
                String[] partes = msg.split(" ", 4);
                String id = partes[1];
                String nomeArquivo = partes[2];
                server.iniciarRecepcaoArquivo(id, nomeArquivo);

            } else if (msg.startsWith("CHUNK")) {
                String[] partes = msg.split(" ", 4);
                String id = partes[1];
                int seq = Integer.parseInt(partes[2]);
                byte[] dados = Base64.getDecoder().decode(partes[3]);
                server.adicionarChunkArquivo(id, seq, dados);

            } else if (msg.startsWith("END")) {
                String[] partes = msg.split(" ", 3);
                String id = partes[1];
                String hash = partes[2];
                server.finalizarRecepcaoArquivo(id, hash);
            }
        } catch (Exception e) {
            System.err.println("Erro ao receber pacote: " + e.getMessage());
        }
    }
}
