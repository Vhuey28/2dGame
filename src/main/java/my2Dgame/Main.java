package my2Dgame;

import java.awt.Color;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import static my2Dgame.GameConfig.WEB_BUILD;

public class Main {
    private static boolean isFullscreen;
    private static Rectangle windowedBounds;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame window = new JFrame();
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            window.setResizable(true);
            window.setTitle("Chronicle Conquest Beta");

            GamePanel gamePanel = new GamePanel();
            gamePanel.setParentWindow(window);
            window.add(gamePanel);
            window.pack();
            window.setBackground(Color.green);
            window.setLocationRelativeTo(null);
            window.setVisible(true);
            gamePanel.requestFocusInWindow();
            gamePanel.startGameThread();
        });
    }

    /** Reliable borderless fullscreen toggle; rendering remains at the game's virtual resolution. */
    public static void toggleFullscreen(JFrame frame, GamePanel gamePanel) {
        if (WEB_BUILD || frame == null || gamePanel == null) return;
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (!isFullscreen) {
            windowedBounds = frame.getBounds();
            frame.dispose();
            frame.setUndecorated(true);
            frame.setResizable(false);
            frame.setVisible(true);
            if (device.isFullScreenSupported()) device.setFullScreenWindow(frame);
            else frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            isFullscreen = true;
        } else {
            if (device.getFullScreenWindow() == frame) device.setFullScreenWindow(null);
            frame.dispose();
            frame.setUndecorated(false);
            frame.setResizable(true);
            frame.setExtendedState(JFrame.NORMAL);
            if (windowedBounds != null) frame.setBounds(windowedBounds);
            else {
                frame.pack();
                frame.setLocationRelativeTo(null);
            }
            frame.setVisible(true);
            isFullscreen = false;
        }
        gamePanel.requestFocusInWindow();
    }
}
