package edu.escuelaing.arem.ASE.app;

import java.io.*;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;



public class HttpServer {
    private static ConcurrentHashMap<String, StringBuffer> cache;
    private static HttpServer _instace = new HttpServer();
    private static HashMap<String, WebService> services = new HashMap<String, WebService>();
    private static HashMap<String,Method> components = new HashMap<String,Method>();
    private static String route = "";

    private HttpServer() {
    }


    public void runServer() throws IOException, URISyntaxException, ClassNotFoundException {
        // Inicializa el socket del servidor y el caché de respuestas
        ServerSocket serverSocket = null;
        cache = new ConcurrentHashMap<String, StringBuffer>();

        Set<String> fileSet = new HashSet<>();
        try (DirectoryStream<Path> stream = Files
                .newDirectoryStream(Paths.get("target/classes/edu/escuelaing/arem/ASE/app"))) {
            for (Path path : stream) {
                if (!Files.isDirectory(path)) {
                    fileSet.add(path.toString());
                }
            }
        }

        for (String file : fileSet) {

            String classFullName = file.replace(".class", "").replace("target\\classes\\", "").replace("\\", ".");

            Class<?> c = Class.forName(classFullName);

            if (c.isAnnotationPresent(Component.class)) {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(GetMapping.class)) {
                        components.put(m.getAnnotation(GetMapping.class).value(), m);
                    }
                }
            }
        }



        try {
            // Intenta escuchar en el puerto 35000
            serverSocket = new ServerSocket(35000);
        } catch (IOException e) {
            // Imprime un mensaje de error si no puede escuchar en el puerto
            System.err.println("Could not listen on port: 35000.");
            System.exit(1);
        }


        // Loop principal que espera y procesa las solicitudes de los clientes
        boolean running = true;
        while (running) {
            // Acepta una conexión entrante del cliente
            Socket clientSocket = null;
            try {
                System.out.println("Listo para recibir ...");
                clientSocket = serverSocket.accept();
            } catch (IOException e) {
                // Imprime un mensaje de error si no se puede aceptar la conexión
                System.err.println("Accept failed.");
                System.exit(1);
            }

            // Inicializa los flujos de entrada y salida para la comunicación con el cliente
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(
                            clientSocket.getInputStream()));

            // Variables para el procesamiento de la solicitud y la respuesta
            String inputLine, outputLine, outputLine1;

            boolean request = true;
            String MovietoSearch = "";


            // Lee y procesa las líneas de la solicitud HTTP del cliente
            String lineOne = "";
            while ((inputLine = in.readLine()) != null) {
                if (request) {
                    MovietoSearch = inputLine.split(" ")[1];
                    request = false;
                    System.out.println("@@@@@ " + MovietoSearch);
                }

                System.out.println("Received: " + inputLine);
                if (!in.ready()) {
                    break;
                }
            }
            URI requestUri = new URI(MovietoSearch);
            String path = requestUri.getPath();
            String query = requestUri.getQuery();
            if (query != null) {
                query = query.split("=")[1];
            } else {
                query = "";
            }
            outputLine = "";
            try {
                if (path.startsWith("/action")) {
                    String webUri = path.replace("/action", "");
                    if (services.containsKey(webUri)) {
                        String p = "codigo para sacar parametro";
                        outputLine = services.get(webUri).handle(p);
                    }
                }else if (path.startsWith("/component")) {
                    String webUri =path.replace("/component", "");
                    Method methodLlamado = components.get(webUri);
                    if (methodLlamado != null) {
                        outputLine = "HTTP/1.1 200 OK\r\n"
                                + "Content-Type:text/html\r\n"
                                + "\r\n";

                        if (methodLlamado.getParameterCount() == 1) {
                            outputLine += methodLlamado.invoke(null, (Object) query);
                        } else {
                            outputLine += methodLlamado.invoke(null);
                        }
                    }
                }else if (path.startsWith("/")) {
                    setroute("src/main/resources");
                    outputLine = handleStaticRequest(path, clientSocket);

                } else {
                    outputLine = httpError();
                }
            } catch (Exception e) {
                e.printStackTrace();
                outputLine = httpError();
            }


            out.println(outputLine);

            // Cierra los flujos y el socket del cliente
            out.close();
            in.close();
            clientSocket.close();
        }
        // Cierra el socket del servidor al finalizar
        serverSocket.close();

    }

    public static String handleStaticRequest(String path, Socket clientSocket) throws IOException {
        String content_type = "";
        if (path.endsWith(".html")) {
            content_type = "text/html";
        } else if (path.endsWith(".js")) {
            content_type = "application/javascript";
        } else if (path.endsWith(".css")) {
            content_type = "text/css";
        } else if (path.endsWith(".jpg")) {
            content_type = "image/jpg";
        }

        String outputLine = "HTTP/1.1 200 OK\r\n"
                + "Content-Type:" + content_type + "\r\n"
                + "\r\n";


        Path file = Paths.get(route + path);
        System.out.println(file);

        Charset charset;
        if (content_type.equals("text/html")) {
            charset = Charset.forName("UTF-8");
        } else if (content_type.equals("application/javascript")) {
            charset = Charset.forName("UTF-8");
        } else if (content_type.equals("text/css")) {
            charset = Charset.forName("UTF-8");
        } else {
            charset = Charset.defaultCharset();
        }


        if (true) {
            if (content_type.equals("image/jpg")) {
                byte[] imageData = Files.readAllBytes(file);
                OutputStream output = clientSocket.getOutputStream();
                output.write(outputLine.getBytes());
                output.write(imageData);
                output.flush();
                output.close();
            } else {
                BufferedReader reader = Files.newBufferedReader(file, charset);
                String line = null;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                    outputLine += line + "\r\n";
                }
                OutputStream output = clientSocket.getOutputStream();
                output.write(outputLine.getBytes());
                output.flush();
                output.close();
            }
        } else {
            outputLine = httpError();
        }

        return outputLine;

    }

    public static String httpError() {
        return "HTTP/1.1 404 NOT FOUND\r\n" // Necesario para los nuevos navegadores
                + "Content-Type:text/html\r\n"
                + "\r\n" // Necesario para los nuevos navegadores
                + "<!DOCTYPE html>"
                + "<html>\n"
                + "<style> body {background-color: rgb (255, 122 89)} </style>"
                + "<body>"
                + "<h1> ERROR  </h1>"
                + "<h2>  <h2>"
                + "</body>"
                + "</html>";
    }

    public String handlePost(String route, String requestBody) {
        WebService handler = services.get(route);
        if (handler != null) {
            // Crear una nueva solicitud con los datos del cuerpo de la solicitud
            Request request = new Request(requestBody);
            // Ejecutar el manejador y devolver la respuesta
            return handler.handle(request.toString());
        } else {
            return "404 Not Found"; // Manejador no encontrado para la ruta especificada
        }
    }

    public static void get(String r, WebService s) {
        services.put(r, s);

    }

    public static void post(String r, WebService s) {
        services.put(r, s);
    }

    public static HttpServer getInstance() {
        return _instace;
    }

    public static void setroute(String directory) {
        if (directory != null && !directory.isEmpty()) {
            route = directory;
        }
    }



}
