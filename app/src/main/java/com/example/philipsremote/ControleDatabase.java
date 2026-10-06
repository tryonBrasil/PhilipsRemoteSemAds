package com.example.philipsremote;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class ControleDatabase extends SQLiteOpenHelper {
    private static final String NAME = "ir_remote.db";
    private static final int VERSION = 2;

    public ControleDatabase(Context context) {
        super(context.getApplicationContext(), NAME, null, VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE controls (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "nome TEXT NOT NULL," +
                "categoria TEXT," +
                "marca TEXT," +
                "modelo TEXT," +
                "perfil TEXT," +
                "descricao TEXT," +
                "codigo INTEGER NOT NULL DEFAULT -1," +
                "frequencia INTEGER NOT NULL DEFAULT 38000," +
                "created INTEGER NOT NULL," +
                "updated INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE commands (" +
                "control_id INTEGER NOT NULL," +
                "funcao TEXT NOT NULL," +
                "codigo INTEGER NOT NULL," +
                "perfil TEXT," +
                "frequencia INTEGER NOT NULL DEFAULT 0," +
                "PRIMARY KEY(control_id, funcao)," +
                "FOREIGN KEY(control_id) REFERENCES controls(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX idx_commands_control ON commands(control_id)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion < 2){
            db.execSQL("ALTER TABLE controls ADD COLUMN updated INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE controls SET updated=created WHERE updated=0");
        }
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }
}
