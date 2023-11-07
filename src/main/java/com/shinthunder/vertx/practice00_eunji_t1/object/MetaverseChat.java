package com.shinthunder.vertx.practice00_eunji_t1.object;

import java.io.Serializable;

public class MetaverseChat implements Serializable {

    private String action;
    private int userId;
    private String nickname;
    private String receiver_nickname;
    private int changeRoomNumber;

    private int roomNumber;
    private String timestamp;
    private int receiver_id;
    private String text;

    public int getChangeRoomNumber() {
        return changeRoomNumber;
    }

    public void setChangeRoomNumber(int changeRoomNumber) {
        this.changeRoomNumber = changeRoomNumber;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getReceiver_nickname() {
        return receiver_nickname;
    }

    public void setReceiver_nickname(String receiver_nickname) {
        this.receiver_nickname = receiver_nickname;
    }

    public int getReceiver_id() {
        return receiver_id;
    }

    public void setReceiver_id(int receiver_id) {
        this.receiver_id = receiver_id;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }



    public int getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(int roomNumber) {
        this.roomNumber = roomNumber;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
