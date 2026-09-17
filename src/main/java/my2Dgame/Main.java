package my2Dgame;

import java.awt.Color;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import javax.swing.JFrame;

import static my2Dgame.GameConfig.WEB_BUILD;

public class Main {

    private static boolean isFullscreen = false;

    public static void main(String[] args) {
        JFrame window  = new JFrame();
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setResizable(false);
        window.setTitle("Chronicle Conquest Beta");

        GamePanel gamePanel = new GamePanel();
        window.add(gamePanel);
        window.pack();
        window.setBackground(Color.green);

        window.setLocationRelativeTo(null);
        window.setVisible(true);

        gamePanel.startGameThread();

        // Add fullscreen toggle via F11
        window.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (!WEB_BUILD && e.getKeyCode() == java.awt.event.KeyEvent.VK_F11) {
                    toggleFullscreen(window, gamePanel);
                }
            }
        });
    }

    public static void toggleFullscreen(JFrame frame, GamePanel gamePanel) {
        if (WEB_BUILD) {
            return;
        }
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (!isFullscreen) {
            frame.dispose();
            frame.setUndecorated(true);
            device.setFullScreenWindow(frame);
            frame.setVisible(true);
            isFullscreen = true;
        } else {
            device.setFullScreenWindow(null);
            frame.setUndecorated(false);
            frame.setSize(gamePanel.screenWidth, gamePanel.screenHeight);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            isFullscreen = false;
        }
    }
}
