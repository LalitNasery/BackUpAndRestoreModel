package com.seiri.backup_restore.ui;

import javax.swing.*;
import java.awt.*;

public class ProgressDialog extends JDialog {
	private JLabel messageLabel;
	private JProgressBar progressBar;

	public ProgressDialog(Frame parent, String title) {
		super(parent, title, true);
		initComponents();
	}

	private void initComponents() {
		setLayout(new BorderLayout(10, 10));
		setSize(400, 120);
		setLocationRelativeTo(getParent());
		setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		setResizable(false);

		// Message label
		messageLabel = new JLabel("Processing...", SwingConstants.CENTER);
		messageLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
		add(messageLabel, BorderLayout.NORTH);

		// Progress bar
		progressBar = new JProgressBar();
		progressBar.setIndeterminate(true);
		progressBar.setBorder(BorderFactory.createEmptyBorder(5, 20, 10, 20));
		add(progressBar, BorderLayout.CENTER);
	}

	public void setMessage(String message) {
		SwingUtilities.invokeLater(() -> messageLabel.setText(message));
	}

	public void showProgress() {
		SwingUtilities.invokeLater(() -> setVisible(true));
	}

	public void hideProgress() {
		SwingUtilities.invokeLater(() -> {
			setVisible(false);
			dispose();
		});
	}
}