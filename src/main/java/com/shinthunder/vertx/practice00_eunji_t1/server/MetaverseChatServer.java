package com.shinthunder.vertx.practice00_eunji_t1.server;

import com.hazelcast.config.Config;
import com.shinthunder.vertx.practice00_eunji_t1.object.ClientAction;
import com.shinthunder.vertx.practice00_eunji_t1.object.MetaverseChat;
import io.vertx.core.*;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MetaverseChatServer extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(MetaverseChatServer.class);
    private static final int WEBSOCKET_PORT = 60004;
    private static final int NUM_OF_INSTANCES = 1; // 버티클 개수


    //현재 연결되어 있는 웹소켓 클라이언트들의 집합
    // 새로운 클라이언트가 연결 될 때마다 이 집합에 추가, 연결을 끊을 때 제거됨
    private final Set<ServerWebSocket> clients = new HashSet<>();

    // 비동기 맵 인터페이스 Key : 사용자 id , Value : 소켓 주소
    // 서로 다른 Verticle이나 노드에서도 동일한 데이터에 접근이 가능
    private AsyncMap<Integer, String> userToSocketMap;  // <userId, socketAddress>
    private AsyncMap<Integer, Set<Integer>> roomToUsersMap;  // roonNum to set of userIDs
    private AsyncMap<Integer, JsonObject> userInfoMap; // <userId, locationData>



    private void handleClientAction(ServerWebSocket socket, MetaverseChat metaverseChat) {
        logger.info("!!!!!!!!!!!!!!!!!!!!!!!! handleClientAction!!!!!!!!!!!!!!!!!!!!");
//        Buffer buffer = Json.encodeToBuffer(clientAction);


        int userId = metaverseChat.getUserId();;
        String nickname = metaverseChat.getNickname();
        String receiver_nickname = metaverseChat.getReceiver_nickname();

        String action= metaverseChat.getAction();
        String text= metaverseChat.getText();
        String timestamp= metaverseChat.getTimestamp();
        int roomNumber= metaverseChat.getRoomNumber();
        int receiver_id= metaverseChat.getReceiver_id();

        logger.info(String.format("userId: %d, nickname: %s, action: %s, text: %s, timestamp: %s, roomNumber: %d, receiver_id: %d",
                userId, nickname, action, text, timestamp, roomNumber, receiver_id));

        try{
            switch (metaverseChat.getAction()) {

                case "SEND_MESSAGE_EVERYONE":
                    try{
                        logger.info(" name : {}, SEND_MESSAGE_EVERYONE !! ", userId);
                        // 맵 데이터에 입장한 유저 정보 저장해줌
                        JsonObject messageData = new JsonObject();
                        messageData.put("action","MESSAGE_EVERYONE");
                        messageData.put("userId",userId);
                        messageData.put("nickname",nickname);
                        messageData.put("roomNumber",roomNumber);
                        messageData.put("text",text);

                        sendMessageToRoomUsers(roomNumber,messageData);

                    } catch (Exception e) {
                        logger.error("Error handling  SEND_MESSAGE_EVERYONE", e);}
                    break;

                case "PREPARE_METAVERSE_CHAT":
                    try{
                        logger.info(" name : {}, PREPARE_METAVERSE_CHAT !! ", userId);
                        // 맵 데이터에 입장한 유저 정보 저장해줌
                        userToSocketMap.put(userId, socket.remoteAddress().toString());
                        addNewUserToRoomToUserMap(roomNumber,userId);
                        initUserInfoMap(userId,nickname,socket);

                        JsonObject roomData = new JsonObject();
                        roomData.put("action","PREPARE_METAVERSE_CHAT_SUCCESS");
                        roomData.put("userId",userId);
                        roomData.put("nickname",nickname);
                        roomData.put("roomNumber",roomNumber);
                        socket.writeTextMessage(roomData.toString());
                    } catch (Exception e) {
                        logger.error("Error handling  PREPARE_METAVERSE_CHAT", e);}
                    break;

                case "CHAT_USERLIST_IN_ROOM":
                    try{
                        logger.info(" name : {}, CHAT_USERLIST_IN_ROOM !! ", userId);
                        // 맵 데이터에 입장한 유저 정보 저장해줌
                        JsonObject data = new JsonObject();
                        data.put("action","ADD_NEW_CHAT_USER_EVENT");
                        data.put("userId",userId);
                        data.put("nickname",nickname);

                        // roomNumber에 해당하는 유저들에게 data 보냄
                        sendMessageToRoomUsers(roomNumber, data);
                        // 해당 방의 유저 리스트 보내줌
                        sendUserListToNewUser(socket,roomNumber);

                    } catch (Exception e) {
                        logger.error("Error handling  CHAT_USERLIST_IN_ROOM", e);}
                    break;

                case "EXIT_METAVERSE":
                    try{
                        logger.info(" name : {}, EXIT_METAVERSE !! ", userId);
                        removeClient(socket);
                        removeUserToSocketMap(userId);
                        removeUserFromRoomToUserMap(roomNumber,userId);
                        removeUserInfoMap(userId);

                        sendMessageRemoveUser(roomNumber,userId);

                    } catch (Exception e) {
                        logger.error("Error handling  CHAT_USERLIST_IN_ROOM", e);}
                    break;


                case "SEND_DIRECT_MESSAGE":
                    try{
                        logger.info(" name : {}, SEND_DIRECT_MESSAGE !! ", userId);
                        // 맵 데이터에 입장한 유저 정보 저장해줌
                        JsonObject data = new JsonObject();
                        data.put("action","SEND_DIRECT_MESSAGE_SUCCESS");
                        data.put("userId",userId);
                        data.put("nickname",nickname);
                        data.put("receiver_id",receiver_id);
                        data.put("receiver_nickname",receiver_nickname);
                        data.put("text",text);
                        data.put("roomNumber",roomNumber);

                        sendMessageToSocket(userId, data);

                        sendMessageToSocket(receiver_id, data);

                    } catch (Exception e) {
                        logger.error("Error handling  CHAT_USERLIST_IN_ROOM", e);}
                    break;

                default:
                    logger.warn("정의되지 않은 Action 값: {}", metaverseChat);
            }
        }
        catch(Exception e){
            logger.error("Unexpected error occurred while processing client action", e);
        }

    }

    // ---------------------- handleData 관련 메서드 ----------------------

    private void sendMessageRemoveUser(int roomNumber,int userId) {
        logger.info("sendMessageRemoveUser ");

        JsonObject ExitUserdata = new JsonObject();
        ExitUserdata.put("action","REMOVE");
        ExitUserdata.put("userId",userId);
        logger.info("ExitUserdata ", ExitUserdata);
        sendMessageToRoomUsers(roomNumber, ExitUserdata);
    }

    private void removeClient(ServerWebSocket socket){
        logger.debug("removeClient");

        clients.remove(socket); // 소켓에서 클라이언트 제거
        logger.info("removeClient 후 clients  값 확인 : {}", clients);
    };
    private void removeUserToSocketMap(int userIdToRemove){
        logger.info("removeUserToSocketMap");

        // userToSocketMap에서 유저 제거
        userToSocketMap.remove(userIdToRemove, res -> {
            if (res.succeeded()) {
                logger.info("userToSocketMap에서 유저 {} 제거 성공", userIdToRemove);
                printUserToSocketMap();

            } else {
                logger.error("userToSocketMap에서 유저 {} 제거 실패: {}", userIdToRemove, res.cause().getMessage());
                printUserToSocketMap();

            }
        });
    };
    private void removeUserFromRoomToUserMap(int roomNum,int userId) {
        logger.info("removeUserFromRoomToUserMap");

        roomToUsersMap.get(roomNum, res -> {
            if (res.succeeded()) {
                System.out.println(roomNum+ "번 방에 해당하는 value 값을 성공적으로 가져왔을 때");
                Set<Integer> userIDs = res.result();
                if (userIDs != null) {
                    userIDs.remove(userId);
                    if (userIDs.isEmpty()) {
                        System.out.println(roomNum+ "번 방에 유저 자신밖에 없을 때 ");
                        roomToUsersMap.remove(roomNum, removeRes -> {
                            if (removeRes.failed()) {
                                // Handle remove failure
                                System.err.println("Failed to remove room: " + removeRes.cause());
                            }
                            printUsersInRoom(roomNum);

                        });
                    } else {
                        roomToUsersMap.put(roomNum, userIDs, putRes -> {
                            if (putRes.failed()) {
                                // Handle put failure
                                System.err.println("Failed to update room: " + putRes.cause());
                            }
                            printUsersInRoom(roomNum);

                        });
                    }
                }
            } else {
                // Handle get failure
                System.err.println("Failed to get users from room: " + res.cause());
            }
        });
    }
    public void removeUserInfoMap(int userIdToRemove){
        logger.info("removeUserInfoMap");
        // UserInfoMap 유저 제거
        userInfoMap.remove(userIdToRemove, res -> {
            if (res.succeeded()) {
                logger.info("userInfoMap 유저 {} 제거 성공", userIdToRemove);
                printUserInfoMap();

            } else {
                logger.error("userInfoMap 유저 {} 제거 실패: {}", userIdToRemove, res.cause().getMessage());
                printUserInfoMap();
            }
        });

    }

    private void addNewUserToRoomToUserMap(int roomNum, int userId) {
        System.out.println("addNewUserToRoomToUserMap" + roomNum + ": " + userId);

        roomToUsersMap.get(roomNum, res -> {
            if (res.succeeded()) {
                System.out.println(roomNum+ "번 방에 해당하는 value 값을 성공적으로 가져왔을 때");

                Set<Integer> userIDs = res.result();
                if (userIDs == null) {
                    System.out.println(roomNum+"번 방에 유저 자신밖에 없을 때");
                    userIDs = new HashSet<>();
                }
                userIDs.add(userId);
                System.out.println(roomNum+"번 방에 있는 userId 리스트" + userIDs);

                roomToUsersMap.put(roomNum, userIDs, putRes -> {
                    if (putRes.failed()) {
                        System.err.println("roomToUsersMap에 값을 추가하는데 실패 한 경우 " + putRes.cause());
                    }
                    System.out.println("roomToUsersMap에 값을 성공적으로 추가한 경우 ");
                    printUsersInRoom(roomNum);

                });
            } else {
                // Handle get failure
                System.out.println(roomNum+ "번 방에 해당하는 value 값을 가져오는데 실패한 경우" + res.cause());
                printUsersInRoom(roomNum);

            }
        });
    }

    private void printUsersInRoom(Integer roomNum) {
        System.out.println("printUsersInRoom");

        roomToUsersMap.get(roomNum, res -> {
            if (res.succeeded()) {
                Set<Integer> userIDs = res.result();
                if (userIDs != null) {
                    // 성공적으로 사용자 목록을 가져왔을 때의 로직
                    System.out.println("Users in room " + roomNum + ": " + userIDs);
                } else {
                    System.out.println("No users found in room " + roomNum);
                }
            } else {
                // 사용자 목록을 가져오는데 실패했을 때의 에러 처리
                System.err.println("Failed to get users from room " + roomNum + ": " + res.cause());
            }
        });
    }

    private void sendMessageToRoomUsers(Integer roomNumber, JsonObject message) {
        logger.info("특정 방에 속한 모든 사용자에게 메세지 보냄");
        getUsersInRoom(roomNumber).onComplete(roomUsers -> {
            if (roomUsers.succeeded()) {
                Set<Integer> users = roomUsers.result();
                if (users == null || users.isEmpty()) {
                    logger.warn("방 번호 {}에 사용자가 없습니다.", roomNumber);
                    return;
                }
                for (Integer userId : users) {
                    sendMessageToSocket(userId, message);
                }
            } else {
                logger.error("방의 사용자 목록 조회 실패: {}", roomUsers.cause().getMessage());
            }
        });
    }

    private Future<Set<Integer>> getUsersInRoom(Integer roomNumber) {
        System.out.println("getUsersInRoom");

        Promise<Set<Integer>> promise = Promise.promise();
        roomToUsersMap.get(roomNumber, ar -> {
            if (ar.succeeded()) {
                promise.complete(ar.result());
            } else {
                promise.fail(ar.cause());
            }
        });
        return promise.future();
    }

    private void sendUserListToNewUser(ServerWebSocket socket, int roomNumber) {
        // 먼저, 해당 방의 유저 목록을 가져옵니다.
        logger.info("sendUserListToNewUser : 당 방의 유저 목록을 가져오고 새로 접속한 유저에게 보내줌");

        getUsersInRoom(roomNumber).onComplete(roomUsers -> {
            if (roomUsers.succeeded()) {
                logger.info("sendUserListToNewUser : 성공적으로 사용자 목록을 가져왔습니다.");
                Set<Integer> usersInRoom = roomUsers.result();

                // 해당 방의 유저 정보만 userLocationMap에서 가져옵니다.
                userInfoMap.entries(res -> {
                    logger.info("sendUserListToNewUser / 해당 방의 유저 정보만 userInfoMap에서 가져옴");

                    if (res.succeeded()) {
                        logger.info("sendUserListToNewUser /해당 방의 유저 정보만 성공적으로 가져왔을 때");

                        Map<Integer, JsonObject> entries = res.result();
                        JsonArray players = new JsonArray();

                        for (Map.Entry<Integer, JsonObject> entry : entries.entrySet()) {
                            if (usersInRoom.contains(entry.getKey())) {  // 해당 방의 유저만 처리
                                JsonObject playerInfo = entry.getValue();
                                logger.debug("sendUserListToNewUser / userId: {}와 userNick:{}와 playerInfo: {}로 엔트리를 처리 중입니다", entry.getKey(), playerInfo);
                                playerInfo.put("userId", entry.getKey());
                                players.add(playerInfo);
                                logger.info(String.valueOf(players));
                            }
                        }

                        try {
                            JsonObject exitUserInfo = new JsonObject();
                            exitUserInfo.put("action", "CHAT_USERLIST_IN_ROOM_SUCCESS");
                            exitUserInfo.put("players", players);

                            socket.writeTextMessage(exitUserInfo.toString());
                            logger.info("sendUserListToNewUser / CHAT_USERLIST_IN_ROOM_SUCCESS 보냄");
                        } catch (Exception e) {
                            logger.error(e.getMessage());
                        }

                    } else {
                        logger.error("sendUserListToNewUser / Error retrieving location data: {}", res.cause().getMessage());
                    }
                });
            } else {
                logger.error("sendUserListToNewUser / Failed to get users in room: {}", roomUsers.cause().getMessage());
            }
        });
    }

    //userId를 통해 소켓을 찾아 메세지를 전송
    private void sendMessageToSocket(Integer userId, JsonObject message) {
        logger.info("sendMessageToSocket");

        userToSocketMap.get(userId, result -> {
            if (result.succeeded() && result.result() != null) {// 소켓 주소 조회가 성공하고 결과가 null이 아닌 경우
                String clientAddress = result.result();// 사용자의 소켓 주소를 가져옵니다.
                // 현재 연결된 모든 클라이언트 소켓들을 순회합니다.
                for (ServerWebSocket clientSocket : clients) {
                    // 만약 조회한 소켓 주소와 연결된 클라이언트 소켓의 주소가 일치하는 경우
                    if (clientSocket.remoteAddress().toString().equals(clientAddress)) {
                        try {
                            clientSocket.writeTextMessage(message.toString());
                            logger.info("사용자 {}에게 메시지 전송 성공: {}", userId, message.encode());
                        } catch (Exception e) {
                            logger.error("클라이언트에 메시지 전송 실패 {}: {}", clientSocket.remoteAddress().host(), e.getMessage());
                        }
                    }
                }
            } else {
                if (result.failed()) {
                    logger.error("사용자 {}에 대한 소켓 주소 조회 실패: {}", userId, result.cause().getMessage());
                }
            }
        });
    }

    private void initUserInfoMap(int userId,String nickname,ServerWebSocket socket){
        logger.info("initUserInfoMap");
        JsonObject locationData = new JsonObject()
                .put("nickname", nickname);
        userInfoMap.put(userId, locationData);
        logger.info(String.valueOf(socket));
    }



    //--------------------------Map 값 확인 메서드 --------------------

    public void printUserToSocketMap(){
        logger.debug("printUserToSocketMap ");
        userToSocketMap.entries(res -> {
            if (res.succeeded()) {
                Map<Integer, String> entries = res.result();
                logger.info("userToSocketMap 값 : {}", entries);

            } else {
                logger.error("userToSocketMap entries 가져오는 실패함", res.cause().getMessage());
            }
        });
    }


    public void printUserInfoMap(){
        logger.debug("printUserLocationMap ");
        userInfoMap.entries(res -> {
            if (res.succeeded()) {
                Map<Integer, JsonObject> entries = res.result();
                logger.info("userInfoMap 값 : {}", entries);

            } else {
                logger.error("printUserInfoMap 에서 entries 가져오는 실패함", res.cause().getMessage());
            }
        });
    }

    // -------------------------- Main METHODS --------------------------
    public static void main(String[] args) {
        setupVertxCluster();
    }

    private static void setupVertxCluster() {
        VertxOptions options = configureVertxOptions();
        Vertx.clusteredVertx(options).onComplete(res -> {
            if (res.succeeded())
                res.result().deployVerticle(MetaverseChatServer.class.getName(), new DeploymentOptions().setInstances(NUM_OF_INSTANCES));
            else logger.error("Cluster up failed: ", res.cause());
        });
    }

    private static VertxOptions configureVertxOptions() {
        Config hazelcastConfig = new Config();
        hazelcastConfig.setClusterName("metaverse-chat");
//        hazelcastConfig.setCPSubsystemConfig(new CPSubsystemConfig().setCPMemberCount(3)); // 여기 주석을 쳐야
        ClusterManager mgr = new HazelcastClusterManager(hazelcastConfig);
        return new VertxOptions().setClusterManager(mgr);
    }


    //-------------------------------------------------
    @Override
    public void start() {
        initializeSharedData();
        configureWebSocketServer();
    }

    private void initializeSharedData() {
        vertx.sharedData().<Integer, String>getAsyncMap("userToSocketMap", res -> {
            if (res.succeeded()) userToSocketMap = res.result();
            else logger.error("Error initializing userToSocketMapAsync:", res.cause());
        });
        vertx.sharedData().<Integer, Set<Integer>>getAsyncMap("roomToUsersMap", res -> {
            if (res.succeeded()) roomToUsersMap = res.result();
            else logger.error("Error initializing userToRoomMap:", res.cause());
        });

        vertx.sharedData().<Integer, JsonObject>getAsyncMap("userInfoMap", res -> {
            if (res.succeeded()) userInfoMap = res.result();
            else logger.error("Error initializing userInfoMap:", res.cause());
        });

    }

    private void configureWebSocketServer() {
        vertx.createHttpServer().webSocketHandler(this::webSocketHandler).exceptionHandler(e -> logger.error("Error occurred with server: {}", e.getMessage())).listen(WEBSOCKET_PORT, res -> {
            if (res.succeeded()) {
                logger.info("Server is now listening on port {}", WEBSOCKET_PORT);
            } else {
                logger.error("Failed to bind on port PORT: {}", res.cause().getMessage());
            }
        });
    }


    public void webSocketHandler(ServerWebSocket socket) {
        logger.info("Client connected: {}", socket.remoteAddress());
        clients.add(socket);
//        logger.info("clients.size() : {}", clients.size());
        socket.handler(buffer -> {
            try {
                logger.info("Received raw message: {}", buffer.toString());
                MetaverseChat metaverseChat = Json.decodeValue(buffer.toString(), MetaverseChat.class);
                handleClientAction(socket, metaverseChat);
//                broadcastMovement(chatItem);
            } catch (Exception e) {
                logger.error("Failed to process message from {}: {}", socket.remoteAddress().host(), e.getMessage(), e);
            }
        });

        socket.exceptionHandler(e -> {
            logger.error("Error occurred with client {}: {}", socket.remoteAddress().host(), e.getMessage());
        });
        socket.closeHandler(v -> {
            userToSocketMap.values().result().remove(socket);  // Remove socket from user mapping
            clients.remove(socket);
            logger.info("Client disconnected: {}", socket.remoteAddress().host());
        });
    }


}
