package com.shinthunder.vertx.practice00_eunji_t1.client;

import com.shinthunder.vertx.practice00_eunji_t1.object.ChatItem;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocket;
import io.vertx.core.json.Json;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Scanner;

public class ChatClient extends AbstractVerticle {

    private final Logger logger = LoggerFactory.getLogger(ChatClient.class);
    private static final int PORT = 8080;
    private static final int PORT2 = 8081;
    private static final String HOST = "localhost";
    private static final String downloadDirectory = "/Users/hawaii/Downloads/FileUpDownload-testbed-client-to-download/";
    // /sendImage
    // /Users/hawaii/Downloads/FileUpDownload-testbed-client-to-upload/1.png

    // --- Username related methods ---
    private void promptForUsername() {
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter your username: ");
            String username = scanner.nextLine();
            logger.info("Username entered: {}", username);
            vertx.runOnContext(v -> handleUsername(username));
        }).start();
    }

    private void handleUsername(String username) {
        vertx.createHttpClient().webSocket(PORT, HOST, "/", webSocketAsyncResult -> {
            if (webSocketAsyncResult.succeeded()) {
                logger.info("Connected to WebSocket as user: {}", username);
                showMenu(webSocketAsyncResult.result(), username);
            } else {
                logger.error("Failed to connect to WebSocket", webSocketAsyncResult.cause());
                System.exit(1);
            }
        });
    }

    // --- Menu related methods ---
    private void showMenu(WebSocket webSocket, String username) {
        System.out.println("Choose an option:");
        System.out.println("1. Create room");
        System.out.println("2. Join room");
        System.out.println("3. Leave room");
        System.out.println("4. Disconnect");

        readInputFromUserToExecuteBlocking(option -> {
            try {
                handleMenuOption(option, webSocket, username);
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("Error reading menu option", e);
            }
        });
    }

    private void handleMenuOption(String option, WebSocket webSocket, String username) {
        switch (option) {
            case "1":
                logger.info("Option 'Create room' selected by user: {}", username);
                System.out.println("Enter room name to create:");
                handleRoomInput("Enter room name to create:", "create", webSocket, username);
                break;
            case "2":
                logger.info("Option 'Join room' selected by user: {}", username);
                System.out.println("Enter room name to join:");
                handleRoomInput("Enter room name to join:", "join", webSocket, username);
                break;
            case "3":
                logger.info("Option 'Leave room' selected by user: {}", username);
                System.out.println("Enter room name to leave:");
                handleRoomInput("Enter room name to leave:", "leave", webSocket, username);
                break;
            case "4":
                logger.info("Option 'Disconnect' selected by user: {}", username);
                ChatItem chatItem = new ChatItem();
                chatItem.setAction("disconnect");
                webSocket.writeTextMessage(Json.encode(chatItem));
                webSocket.close();
                System.exit(0);
                break;
            default:
                System.out.println("Invalid option. Try again.");
                showMenu(webSocket, username);
        }
    }

    // --- Chat session related methods ---
    private void handleChatSession(WebSocket webSocket, String username, String roomName) {
        webSocket.textMessageHandler(message -> {
            ChatItem response = Json.decodeValue(message, ChatItem.class);
//            System.out.println("    Received message from server: " + message);
//            System.out.println("        roomName : " + roomName);
            switch (response.getAction()) {
                case "message":
                    System.out.println("    >> " + response.getSenderName() + " : " + response.getMessage());
                    break;
                case "startImageDownload":
                    System.out.println("        >> " + response.getSenderName() + ": Image Name : " + response.getMessage());
                    downloadImage(response.getMessage(), webSocket, username, roomName);
                    break;
            }
        });
        readInputAndSendMessage(webSocket, username, roomName);
    }

    private void readInputAndSendMessage(WebSocket webSocket, String username, String roomName) {
        System.out.println("Enter your messages below (type '/leave' to quit or '/sendImage' to send an image):");
        readInputFromUserToExecuteBlocking(messageText -> {
            if ("/leave".equalsIgnoreCase(messageText)) {
                leaveChatRoom(webSocket, username, roomName);
            } else if ("/sendImage".equalsIgnoreCase(messageText)) {
                handleImageUploadFromChat(webSocket, username, roomName); // Call the new method to handle image upload from chat session
            } else {
                sendMessage(webSocket, username, roomName, "message", messageText);
                readInputAndSendMessage(webSocket, username, roomName);
            }
        });
    }

    private void leaveChatRoom(WebSocket webSocket, String username, String roomName) {
        ChatItem chatItem = new ChatItem();
        chatItem.setSenderName(username);
        chatItem.setRoomName(roomName);
        chatItem.setAction("leave");
        webSocket.writeTextMessage(Json.encode(chatItem));
        showMenu(webSocket, username);
    }

    // --- Room related methods ---
    private void handleRoomInput(String prompt, String action, WebSocket webSocket, String username) {
//        readInputFromUser(res -> {
//            if (res.succeeded()) {
//                String roomName = res.result();
//                sendMessage(webSocket, username, roomName, action, "");
//                if ("join".equals(action)) handleChatSession(webSocket, username, roomName);
//                else showMenu(webSocket, username);
//            } else logger.error("Error reading room name", res.cause());
//        });

        readInputFromUserToExecuteBlocking(roomName -> {
            try {

                sendMessage(webSocket, username, roomName, action, "");
                if ("join".equals(action)) handleChatSession(webSocket, username, roomName);
                else showMenu(webSocket, username);
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("Error reading room name", e);
            }
        });
    }

    // --- Image related methods ---
    private void handleImageUploadFromChat(WebSocket webSocket, String username, String roomName) {
        System.out.println("Enter the image file path:");
        readInputFromUserToExecuteBlocking(imagePath -> {
            try {
                ChatItem chatItem = new ChatItem();
                chatItem.setAction("prepareImageUpload");
                chatItem.setSenderName(username);
                chatItem.setRoomName(roomName);
                webSocket.writeTextMessage(Json.encode(chatItem));

                // HTTP POST 요청을 사용하여 이미지 업로드
                MultipartForm form = MultipartForm.create()
                        .attribute("imageDescription", "a very nice image")
                        .binaryFileUpload(
                                "imageFile",
                                new File(imagePath).getName(),
                                imagePath,
                                getMediaType(imagePath));
                WebClient client = WebClient.create(vertx);
                client.post(PORT2, HOST, "/upload")
                        .sendMultipartForm(form)
                        .onSuccess(response -> {
                            if (response.statusCode() == 200) {
//                                System.out.println("    Image uploaded successfully !!");
                                String fileNameOnServer = response.statusMessage();
//                                System.out.println("    fileNameOnServer    : " + fileNameOnServer);
//                                System.out.println("    roomName            : " + roomName);
                                sendMessage(webSocket, username, roomName, "imageUploaded", fileNameOnServer);
                            } else {
                                System.out.println("Image uploaded smth wrong !!");
                            }
                            readInputAndSendMessage(webSocket, username, roomName); // Return back to the chat session
                        })
                        .onFailure(err -> {
                            logger.error("Error during image upload", err);
                            readInputAndSendMessage(webSocket, username, roomName); // Return back to the chat session on failure as well
                        });
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("Error reading image path", e);
                readInputAndSendMessage(webSocket, username, roomName); // Return back to the chat session if there's an error reading the path
            } finally {
                System.out.println("keep going");
            }

        });
    }

    private void downloadImage(String imagePath, WebSocket webSocket, String username, String roomName) {
        WebClient client = WebClient.create(vertx);
        client.get(PORT2, HOST, "/download/" + imagePath)
                .send(ar -> {
                    if (ar.succeeded()) {
                        HttpResponse<Buffer> response = ar.result();
                        // Assuming you want to save the image to the current directory with the same filename.
                        String fileName = downloadDirectory + imagePath;
                        vertx.fileSystem().writeFile(fileName, response.body(), writeRes -> {
                            if (writeRes.succeeded()) {
//                                System.out.println("        Image downloaded and saved as " + fileName);
                            } else {
                                System.err.println("Failed to save the image: " + writeRes.cause().getMessage());
                            }
                        });
                    } else {
                        System.err.println("Failed to download the image: " + ar.cause().getMessage());
                    }
                });
    }

    private static String getMediaType(String filename) {
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "txt" -> "text/plain";
            case "aac" -> "audio/aac";
            case "mp3" -> "audio/mp3";
            case "mp4" -> "video/mp4";
            case "mov" -> "video/quicktime";
            case ".webm" -> "video/webm";
            default -> "application/octet-stream";
        };
    }

    // --- General utility methods ---
    private void sendMessage(WebSocket webSocket, String username, String roomName, String action, String messageText) {
        ChatItem messageItem = new ChatItem();
        messageItem.setSenderName(username);
        messageItem.setRoomName(roomName);
        messageItem.setAction(action);
        messageItem.setMessage(messageText);
        webSocket.writeTextMessage(Json.encode(messageItem));
    }

    private void readInputFromUserToExecuteBlocking(Handler<String> handler) {
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            String messageText = scanner.nextLine();
            vertx.runOnContext(v -> handler.handle(messageText));
        }).start();
    }

    // --- Start and Main methods ---
    @Override
    public void start() {
        promptForUsername();
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new ChatClient());
    }
}