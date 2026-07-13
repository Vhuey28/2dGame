package my2Dgame;

import java.awt.Color;
import javax.swing.JFrame;

public class Main {

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

	}

}
