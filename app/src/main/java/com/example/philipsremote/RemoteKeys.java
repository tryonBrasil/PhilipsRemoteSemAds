package com.example.philipsremote;

/** Teclas do controle do app: códigos internos (RC6 Philips), chaves salvas e tabela da LG. */
public final class RemoteKeys {
    private RemoteKeys() {}

    public static final int POWER = 0x0C, MUTE = 0x0D, VOL_DOWN = 0x11, VOL_UP = 0x10;
    public static final int CH_DOWN = 0x21, CH_UP = 0x20;
    public static final int UP = 0x58, DOWN = 0x59, LEFT = 0x5A, RIGHT = 0x5B, OK = 0x5C;
    public static final int BACK = 0x0A, MENU = 0x57, HOME = 0x54, SOURCE = 0x38;
    public static final int INFO = 0x0F, GUIDE = 0xCC, NETFLIX = 0x76, SETTINGS = 0xBF;
    public static final int RED = 0x6D, GREEN = 0x6E, YELLOW = 0x6F, BLUE = 0x70;
    public static final int PLAY = 0x2C, STOP = 0x31, PAUSE = 0x30, REWIND = 0x2B;
    public static final int FAST_FORWARD = 0x28, SUBTITLE = 0x4B, EXIT = 0x9F;
    public static final int CC = 0x3C;               // botão "CC" do teclado (dígitos 0..9 usam o próprio valor)

    /** Funções que podem ser aprendidas (rótulo na tela / chave gravada no banco), na mesma ordem. */
    public static final String[] FUNCOES = {
        "Ligar/desligar", "Mute", "Volume +", "Volume -", "Canal +", "Canal -",
        "Cima", "Baixo", "Esquerda", "Direita", "OK", "Voltar", "Menu", "Home", "Source", "Info", "Guide",
        "Netflix", "Configurações", "Vermelho", "Verde", "Amarelo", "Azul",
        "Play", "Pause", "Stop", "Retroceder", "Avançar", "Subtitle", "Exit", "CC",
        "Número 0", "Número 1", "Número 2", "Número 3", "Número 4",
        "Número 5", "Número 6", "Número 7", "Número 8", "Número 9"
    };
    public static final String[] CHAVES = {
        "POWER", "MUTE", "VOL_UP", "VOL_DOWN", "CH_UP", "CH_DOWN",
        "UP", "DOWN", "LEFT", "RIGHT", "OK", "BACK", "MENU", "HOME", "SOURCE", "INFO", "GUIDE",
        "NETFLIX", "SETTINGS", "RED", "GREEN", "YELLOW", "BLUE",
        "PLAY", "PAUSE", "STOP", "REWIND", "FAST_FORWARD", "SUBTITLE", "EXIT", "CC",
        "NUM_0", "NUM_1", "NUM_2", "NUM_3", "NUM_4",
        "NUM_5", "NUM_6", "NUM_7", "NUM_8", "NUM_9"
    };

    /** Ventilador universal: tecla n (1..5) corresponde a FAN_CHAVES[n-1]. */
    public static final String[] FAN_FUNCOES = {"Ligar / desligar", "Oscilação", "Velocidade", "Timer", "Noturno"};
    public static final String[] FAN_CHAVES = {"POWER", "SWING", "SPEED", "TIMER", "NIGHT"};

    /** Chave gravada de um comando do app ("" se não for aprendível). */
    public static String funcao(int command) {
        if (command >= 0 && command <= 9) return "NUM_" + command;
        switch (command) {
            case POWER: return "POWER"; case MUTE: return "MUTE";
            case VOL_UP: return "VOL_UP"; case VOL_DOWN: return "VOL_DOWN";
            case CH_UP: return "CH_UP"; case CH_DOWN: return "CH_DOWN";
            case UP: return "UP"; case DOWN: return "DOWN"; case LEFT: return "LEFT";
            case RIGHT: return "RIGHT"; case OK: return "OK";
            case BACK: return "BACK"; case MENU: return "MENU"; case HOME: return "HOME";
            case SOURCE: return "SOURCE"; case INFO: return "INFO"; case GUIDE: return "GUIDE";
            case NETFLIX: return "NETFLIX"; case SETTINGS: return "SETTINGS";
            case RED: return "RED"; case GREEN: return "GREEN"; case YELLOW: return "YELLOW"; case BLUE: return "BLUE";
            case PLAY: return "PLAY"; case PAUSE: return "PAUSE"; case STOP: return "STOP";
            case REWIND: return "REWIND"; case FAST_FORWARD: return "FAST_FORWARD";
            case SUBTITLE: return "SUBTITLE"; case EXIT: return "EXIT"; case CC: return "CC";
            default: return "";
        }
    }

    /** Comando NEC da LG (endereço 0x04) para cada tecla do app. */
    public static int lgCode(int c) {
        switch (c) {
            case POWER: return 0x08; case MUTE: return 0x09; case VOL_UP: return 0x02; case VOL_DOWN: return 0x03;
            case CH_UP: return 0x00; case CH_DOWN: return 0x01; case UP: return 0x40; case DOWN: return 0x41;
            case LEFT: return 0x07; case RIGHT: return 0x06; case OK: return 0x44; case BACK: return 0x28;
            case MENU: return 0x43; case HOME: return 0x7C; case SOURCE: return 0x0B; case INFO: return 0xAA;
            case GUIDE: return 0xAB; case SETTINGS: return 0x45 /* Q.MENU */; case RED: return 0x72; case GREEN: return 0x71;
            case YELLOW: return 0x63; case BLUE: return 0x61; case PLAY: return 0xB0; case STOP: return 0xB1;
            case PAUSE: return 0xBA; case REWIND: return 0x8F; case FAST_FORWARD: return 0x8E;
            case SUBTITLE: return 0x39; case CC: return 0x39; case EXIT: return 0x5B; case NETFLIX: return 0xB5;
            case 0: return 0x10; case 1: return 0x11; case 2: return 0x12; case 3: return 0x13; case 4: return 0x14;
            case 5: return 0x15; case 6: return 0x16; case 7: return 0x17; case 8: return 0x18; case 9: return 0x19;
            default: return c & 0xFF;
        }
    }
}
