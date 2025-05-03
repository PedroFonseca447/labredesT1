import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public class UdpServer {
    private final int PORT;
    private static final String BROADCAST_IP = "255.255.255.255";
    private static final int BUFFER_SIZE = 1024;

    private DatagramSocket socket;
    private final Map<String, DeviceInfo> dispositivos = new ConcurrentHashMap<>();
    private final Map<String, String> mensagensPendentes = new ConcurrentHashMap<>();
    private final Map<String, RecepcaoArquivo> recebendoArquivos = new ConcurrentHashMap<>();

    public UdpServer(int porta) throws SocketException {
        this.PORT = porta;
        socket = new DatagramSocket(PORT);
        socket.setBroadcast(true);
    }

    public void start() {
        new Thread(this::escutarMensagens).start();
        new Thread(this::heartbeatLoop).start();
        new Thread(this::verificarTimeouts).start();
    }

    private void escutarMensagens() {
        byte[] buffer = new byte[BUFFER_SIZE];
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength());
                MensageHandler.processar(msg, packet.getAddress(), packet.getPort(), this);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void heartbeatLoop() {
        while (true) {
            try {
                enviarHeartbeat();
                Thread.sleep(5000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void verificarTimeouts() {
        while (true) {
            long agora = System.currentTimeMillis();
            dispositivos.values().removeIf(device -> agora - device.timestamp > 10000);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void enviarHeartbeat() throws IOException {
        String nome = InetAddress.getLocalHost().getHostName();
        String mensagem = "HEARTBEAT " + nome;
        byte[] buffer = mensagem.getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(BROADCAST_IP), PORT);
        socket.send(packet);
    }

    public void sendMessage(String mensagem, InetAddress ip, int porta) throws IOException {
        byte[] buffer = mensagem.getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, ip, porta);
        socket.send(packet);
    }

    public void enviarTalk(String nome, String conteudo) {
        DeviceInfo dest = dispositivos.get(nome);
        if (dest == null) {
            System.out.println("[!] Dispositivo não encontrado.");
            return;
        }
        String id = UUID.randomUUID().toString();
        String mensagem = "TALK " + id + " " + conteudo;
        mensagensPendentes.put(id, mensagem);
        new Thread(() -> retryEnvio(id, mensagem, dest)).start();
    }

    private void retryEnvio(String id, String mensagem, DeviceInfo dest) {
        try {
            for (int i = 0; i < 5; i++) {
                if (!mensagensPendentes.containsKey(id)) return;
                sendMessage(mensagem, dest.ip, dest.porta);
                Thread.sleep(2000);
                System.out.println("[Tentativa] Reenviando TALK para " + dest.nome);
            }
            System.out.println("[Erro] Falha ao entregar mensagem: " + id);
            mensagensPendentes.remove(id);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void receiveAckId(String id) {
        mensagensPendentes.remove(id);
        System.out.println("[ACK] Mensagem confirmada: " + id);
    }

    public void registerHeartBeat(String nome, InetAddress ip, int porta) {
        dispositivos.put(nome, new DeviceInfo(nome, ip, porta, System.currentTimeMillis()));
        System.out.println("[+] HEARTBEAT de " + nome);
    }

    public void comandProcess(String input) {
        if (input.startsWith("talk ")) {
            String[] partes = input.split(" ", 3);
            if (partes.length == 3) enviarTalk(partes[1], partes[2]);
        } else if (input.startsWith("sendfile ")) {
            String[] partes = input.split(" ", 3);
            if (partes.length == 3) enviarArquivo(partes[1], partes[2]);
        } else if (input.equals("devices")) {
            dispositivos.forEach((k, v) -> {
                long diff = (System.currentTimeMillis() - v.timestamp) / 1000;
                System.out.printf("%s - %s:%d (%ds atrás)%n", v.nome, v.ip.getHostAddress(), v.porta, diff);
            });
        }
    }

    public void enviarArquivo(String nome, String nomeArquivo) {
        DeviceInfo dest = dispositivos.get(nome);
        if (dest == null) {
            System.out.println("[!] Dispositivo não encontrado.");
            return;
        }

        new Thread(() -> {
            try {
                File arquivo = new File(nomeArquivo);
                if (!arquivo.exists()) {
                    System.out.println("[!] Arquivo não encontrado: " + nomeArquivo);
                    return;
                }

                String id = UUID.randomUUID().toString();
                long tamanho = arquivo.length();
                String msgFile = "FILE " + id + " " + arquivo.getName() + " " + tamanho;
                sendMessage(msgFile, dest.ip, dest.porta);

                try (FileInputStream fis = new FileInputStream(arquivo)) {
                    byte[] buffer = new byte[512];
                    int bytesLidos;
                    int seq = 0;
                    while ((bytesLidos = fis.read(buffer)) != -1) {
                        byte[] dados = Arrays.copyOf(buffer, bytesLidos);
                        String base64 = Base64.getEncoder().encodeToString(dados);
                        String msgChunk = "CHUNK " + id + " " + seq + " " + base64;
                        sendMessage(msgChunk, dest.ip, dest.porta);
                        Thread.sleep(100);
                        seq++;
                    }
                }

                String hash = calcularHash(arquivo);
                String msgEnd = "END " + id + " " + hash;
                sendMessage(msgEnd, dest.ip, dest.porta);
                System.out.println("[+] Arquivo enviado com sucesso.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private String calcularHash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public void iniciarRecepcaoArquivo(String id, String nomeArquivo) {
        recebendoArquivos.put(id, new RecepcaoArquivo(nomeArquivo));
    }

    public void adicionarChunkArquivo(String id, int seq, byte[] dados) {
        RecepcaoArquivo rec = recebendoArquivos.get(id);
        if (rec != null) {
            rec.adicionarChunk(seq, dados);
        }
    }

    public void finalizarRecepcaoArquivo(String id, String hash) {
        RecepcaoArquivo rec = recebendoArquivos.get(id);
        if (rec != null) {
            rec.hashEsperado = hash;
            try {
                rec.salvarEValidar();
            } catch (Exception e) {
                System.out.println("[!] Erro ao salvar o arquivo recebido.");
            }
            recebendoArquivos.remove(id);
        }
    }

    private static class RecepcaoArquivo {
        String nomeArquivo;
        Map<Integer, byte[]> blocos = new TreeMap<>();
        String hashEsperado;

        RecepcaoArquivo(String nomeArquivo) {
            this.nomeArquivo = nomeArquivo;
        }

        void adicionarChunk(int seq, byte[] dados) {
            blocos.put(seq, dados);
        }

        void salvarEValidar() throws Exception {
            File arquivo = new File("recebido_" + nomeArquivo);
            try (FileOutputStream fos = new FileOutputStream(arquivo)) {
                for (byte[] bloco : blocos.values()) {
                    fos.write(bloco);
                }
            }
            String hashRecebido = calcularHash(arquivo);
            if (hashRecebido.equals(hashEsperado)) {
                System.out.println("[✓] Arquivo salvo com sucesso e hash válido.");
            } else {
                System.out.println("[X] Arquivo corrompido (hash inválido).");
                arquivo.delete();
            }
        }

        private String calcularHash(File file) throws Exception {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
    }
}
