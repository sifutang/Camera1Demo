package com.example.cameraonedemo.model;

public class ModeItem {

    private int mId;
    private String mName;

    public ModeItem(String name, int id) {
        mName = name;
        mId = id;
    }

    public String getName() {
        return mName;
    }

    public int getId() {
        return mId;
    }

    @Override
    public String toString() {
        return "ModeItem{" +
                "mId=" + mId +
                ", mName='" + mName + '\'' +
                '}';
    }
}
