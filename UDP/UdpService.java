import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
public class UdpService {

    private static final int PORT = 50000;
    private static final String BROADCAST_IP = "255.255.255.255";
    private static final int BUFFER_SIZE = 1024;

    private final DatagramSocket socket;
    private final Map<String, DeviceInfo> dispositivos = new ConcurrentHashMap<>();
    private final Map<String, String> mensagensPendentes = new ConcurrentHashMap<>();

    public UdpService() throws SocketException {
        socket = new DatagramSocket(PORT);
        socket.setBroadcast(true);
    }

    public void start() {// inicia as threads que vao dar sequencia a cada metodo
        new Thread(this::mensageReceiver).start();
        new Thread(this::heartBeatLoop).start();
        new Thread(this::timeoutCheck).start();
    }

    private void mensageReceiver() {
        byte[] buffer = new byte[BUFFER_SIZE];
        while (true) {//enquanto broadcast for true
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength());
               MensageHandler.processar(msg, packet.getAddress(), packet.getPort(), this);
            } catch (IOException e) {
                System.err.println("Erro ao receber pacote: " + e.getMessage());
            }
        }
    }


    private void heartBeatLoop() {
        while (true) {
            try {
                sendHeartbeat();
                Thread.sleep(5000);
            } catch (Exception e) {
                System.err.println("Erro ao receber pacote: " + e.getMessage());
            }
        }
    }

    private void timeoutCheck() {
        while (true) {
            long agora = System.currentTimeMillis();
            dispositivos.values().removeIf(device -> agora - device.timestamp > 10000);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                System.err.println("Erro ao receber pacote: " + e.getMessage());
            }
        }
    }

    private void sendHeartbeat() throws IOException {
        String nome = InetAddress.getLocalHost().getHostName();
        String mensagem = "HEARTBEAT " + nome;
        byte[] buffer = mensagem.getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(BROADCAST_IP), PORT);
        socket.send(packet);
    }

    public void sendMensage(String mensagem, InetAddress ip, int porta) throws IOException {
        byte[] buffer = mensagem.getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, ip, porta);
        socket.send(packet);
    }

        //para "iniciar" uma conversa
    public void sendTalk(String nome, String conteudo) {
        DeviceInfo dest = dispositivos.get(nome);
        if (dest == null) {
            System.out.println("[!] Dispositivo não encontrado.");
            return;
        }
        String id = UUID.randomUUID().toString();
        String mensagem = "TALK " + id + " " + conteudo;
        mensagensPendentes.put(id, mensagem);
        new Thread(() -> retrySend(id, mensagem, dest)).start();
    }
    // se a excecao na hora e abrir um canal de conversa, fica tentando novamente por 2 segunds 
    private void retrySend(String id, String mensagem, DeviceInfo dest) {
        try {
            for (int i = 0; i < 5; i++) {
                if (!mensagensPendentes.containsKey(id)) return;
                sendMensage(mensagem,dest.ip, dest.porta );
                Thread.sleep(2000);
                System.out.println("[Tentativa] Reenviando TALK para " + dest.nome);
            }
            System.out.println("[Erro] Falha ao entregar mensagem: " + id);
            mensagensPendentes.remove(id);
        } catch (Exception e) {
            System.err.println("Erro ao receber pacote: " + e.getMessage());
        }
    }

    public void receiveAckId(String id) {
        mensagensPendentes.remove(id);
        System.out.println("[ACK] Mensagem confirmada: " + id);
    }

    public void registerHeartBeat(String nome, InetAddress ip, int porta) {
        dispositivos.put(nome, new DeviceInfo( ip,nome, porta, System.currentTimeMillis()));
        System.out.println("[+] HEARTBEAT de " + nome);
    }

    public void comandProcess(String input) {
        if (input.startsWith("talk ")) {
            String[] partes = input.split(" ", 3);
            if (partes.length == 3) sendTalk(partes[1], partes[2]);
        } else if (input.equals("devices")) {
            dispositivos.forEach((k, v) -> {
                long diff = (System.currentTimeMillis() - v.timestamp) / 1000;
                System.out.printf("%s - %s:%d (%ds atrás)%n", v.nome, v.ip.getHostAddress(), v.porta, diff);
            });
        }
    }

}
