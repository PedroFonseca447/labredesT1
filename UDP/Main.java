import java.io.IOException;
import java.util.Scanner;
public class Main {
     public static void main(String[] args) throws IOException {
        UdpService server = new UdpService();
        server.start();
        @SuppressWarnings("resource")
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("Comand> ");
            String input = scanner.nextLine();
            server.comandProcess(input);
        }
}
}
