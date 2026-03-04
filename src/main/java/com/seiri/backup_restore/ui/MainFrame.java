package com.seiri.backup_restore.ui;

import com.seiri.backup_restore.model.MongoConnectionConfig;
import com.seiri.backup_restore.service.BackupRestoreService;
import com.seiri.backup_restore.service.MongoConnectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Component
@Lazy
public class MainFrame extends JFrame {

	private static MainFrame instance = null;
	private static boolean isInstanceCreated = false;

	private final MongoConnectionService connectionService;
	private final BackupRestoreService backupRestoreService;

	// UI Components - Backup Panel
	private JTextField txtHost, txtPort, txtDatabase, txtUsername;
	private JPasswordField txtPassword;
	private JComboBox<String> cmbAuthSource;
	private JButton btnConnect, btnBackup, btnRestore;
	private JTable tblCollections;
	private DefaultTableModel tableModel;
	private JTabbedPane tabbedPane;
	private JCheckBox chkSelectAll;

	@Autowired
	public MainFrame(MongoConnectionService connectionService, BackupRestoreService backupRestoreService) {
		if (isInstanceCreated) {
			throw new IllegalStateException("MainFrame instance already exists!");
		}

		this.connectionService = connectionService;
		this.backupRestoreService = backupRestoreService;
		instance = this;
		isInstanceCreated = true;

		initComponents();
	}

	/**
	 * Get singleton instance
	 */
	public static MainFrame getInstance() {
		return instance;
	}

	/**
	 * Bring window to front if already open
	 */
	public void bringToFront() {
		if (this != null) {
			setState(JFrame.NORMAL);
			toFront();
			requestFocus();
		}
	}

	private void initComponents() {
		setTitle("MongoDB Backup & Restore Tool");
		setSize(900, 650);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLocationRelativeTo(null);

		// Add window listener to reset instance flag on close
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent windowEvent) {
				isInstanceCreated = false;
				instance = null;
			}
		});

		// Create tabbed pane
		tabbedPane = new JTabbedPane();

		// Add tabs
		tabbedPane.addTab("Backup", createBackupPanel());
		tabbedPane.addTab("Restore", createRestorePanel());

		add(tabbedPane);
	}

	/**
	 * Create Backup Panel
	 */
	private JPanel createBackupPanel() {
		JPanel panel = new JPanel(new BorderLayout(10, 10));
		panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		// Connection Panel
		JPanel connectionPanel = new JPanel(new GridBagLayout());
		connectionPanel.setBorder(BorderFactory.createTitledBorder("Database Connection"));
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(5, 5, 5, 5);
		gbc.fill = GridBagConstraints.HORIZONTAL;

		// Row 0: Host and Port
		gbc.gridx = 0;
		gbc.gridy = 0;
		connectionPanel.add(new JLabel("Host:"), gbc);
		gbc.gridx = 1;
		txtHost = new JTextField("localhost", 20);
		connectionPanel.add(txtHost, gbc);

		gbc.gridx = 2;
		connectionPanel.add(new JLabel("Port:"), gbc);
		gbc.gridx = 3;
		txtPort = new JTextField("27017", 10);
		connectionPanel.add(txtPort, gbc);

		// Row 1: Database and Username
		gbc.gridx = 0;
		gbc.gridy = 1;
		connectionPanel.add(new JLabel("Database:"), gbc);
		gbc.gridx = 1;
		txtDatabase = new JTextField(20);
		connectionPanel.add(txtDatabase, gbc);

		gbc.gridx = 2;
		connectionPanel.add(new JLabel("Username:"), gbc);
		gbc.gridx = 3;
		txtUsername = new JTextField(10);
		connectionPanel.add(txtUsername, gbc);

		// Row 2: Password and Auth DB
		gbc.gridx = 0;
		gbc.gridy = 2;
		connectionPanel.add(new JLabel("Password:"), gbc);
		gbc.gridx = 1;
		txtPassword = new JPasswordField(20);
		connectionPanel.add(txtPassword, gbc);

		gbc.gridx = 2;
		connectionPanel.add(new JLabel("Auth DB:"), gbc);
		gbc.gridx = 3;
		cmbAuthSource = new JComboBox<>(new String[] { "Same as Database", "admin" });
		connectionPanel.add(cmbAuthSource, gbc);

		// Row 3: Connect Button
		gbc.gridx = 1;
		gbc.gridy = 3;
		gbc.gridwidth = 3;
		btnConnect = new JButton("Connect & Load Collections");
		btnConnect.addActionListener(e -> loadCollections());
		connectionPanel.add(btnConnect, gbc);

		panel.add(connectionPanel, BorderLayout.NORTH);

		// Collections Table with Select All
		JPanel tablePanel = new JPanel(new BorderLayout());
		tablePanel.setBorder(BorderFactory.createTitledBorder("Collections"));

		// Select All Checkbox Panel
		JPanel selectAllPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		chkSelectAll = new JCheckBox("Select All");
		chkSelectAll.addActionListener(e -> selectAllCollections(chkSelectAll.isSelected()));
		selectAllPanel.add(chkSelectAll);
		tablePanel.add(selectAllPanel, BorderLayout.NORTH);

		tableModel = new DefaultTableModel(new Object[] { "Select", "Collection Name", "Document Count" }, 0) {
			@Override
			public Class<?> getColumnClass(int column) {
				return column == 0 ? Boolean.class : String.class;
			}

			@Override
			public boolean isCellEditable(int row, int column) {
				return column == 0;
			}

			@Override
			public void setValueAt(Object aValue, int row, int column) {
				super.setValueAt(aValue, row, column);
				if (column == 0) {
					updateSelectAllCheckbox();
				}
			}
		};

		tblCollections = new JTable(tableModel);
		tblCollections.setRowHeight(25);
		tblCollections.getColumnModel().getColumn(0).setMaxWidth(50);
		tblCollections.getColumnModel().getColumn(0).setPreferredWidth(50);

		JScrollPane scrollPane = new JScrollPane(tblCollections);
		tablePanel.add(scrollPane, BorderLayout.CENTER);

		panel.add(tablePanel, BorderLayout.CENTER);

		// Backup Button
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		btnBackup = new JButton("Backup Selected Collections");
		btnBackup.setEnabled(false);
		btnBackup.addActionListener(e -> performBackup());
		buttonPanel.add(btnBackup);

		panel.add(buttonPanel, BorderLayout.SOUTH);

		return panel;
	}

	/**
	 * Select or deselect all collections
	 */
	private void selectAllCollections(boolean select) {
		for (int i = 0; i < tableModel.getRowCount(); i++) {
			tableModel.setValueAt(select, i, 0);
		}
	}

	/**
	 * Update Select All checkbox based on table state
	 */
	private void updateSelectAllCheckbox() {
		if (tableModel.getRowCount() == 0) {
			chkSelectAll.setSelected(false);
			return;
		}

		boolean allSelected = true;
		for (int i = 0; i < tableModel.getRowCount(); i++) {
			Boolean selected = (Boolean) tableModel.getValueAt(i, 0);
			if (selected == null || !selected) {
				allSelected = false;
				break;
			}
		}
		chkSelectAll.setSelected(allSelected);
	}

	/**
	 * Create Restore Panel
	 */
	private JPanel createRestorePanel() {
		JPanel panel = new JPanel(new BorderLayout(10, 10));
		panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		// Connection Panel
		JPanel connectionPanel = new JPanel(new GridBagLayout());
		connectionPanel.setBorder(BorderFactory.createTitledBorder("Database Connection"));
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(5, 5, 5, 5);
		gbc.fill = GridBagConstraints.HORIZONTAL;

		// Row 0: Host and Port
		gbc.gridx = 0;
		gbc.gridy = 0;
		connectionPanel.add(new JLabel("Host:"), gbc);
		gbc.gridx = 1;
		JTextField txtRestoreHost = new JTextField("localhost", 20);
		connectionPanel.add(txtRestoreHost, gbc);

		gbc.gridx = 2;
		connectionPanel.add(new JLabel("Port:"), gbc);
		gbc.gridx = 3;
		JTextField txtRestorePort = new JTextField("27017", 10);
		connectionPanel.add(txtRestorePort, gbc);

		// Row 1: Database and Username
		gbc.gridx = 0;
		gbc.gridy = 1;
		connectionPanel.add(new JLabel("Database:"), gbc);
		gbc.gridx = 1;
		JTextField txtRestoreDatabase = new JTextField(20);
		connectionPanel.add(txtRestoreDatabase, gbc);

		gbc.gridx = 2;
		connectionPanel.add(new JLabel("Username:"), gbc);
		gbc.gridx = 3;
		JTextField txtRestoreUsername = new JTextField(10);
		connectionPanel.add(txtRestoreUsername, gbc);

		// Row 2: Password and Auth DB
		gbc.gridx = 0;
		gbc.gridy = 2;
		connectionPanel.add(new JLabel("Password:"), gbc);
		gbc.gridx = 1;
		JPasswordField txtRestorePassword = new JPasswordField(20);
		connectionPanel.add(txtRestorePassword, gbc);

		gbc.gridx = 2;
		connectionPanel.add(new JLabel("Auth DB:"), gbc);
		gbc.gridx = 3;
		JComboBox<String> cmbRestoreAuthSource = new JComboBox<>(new String[] { "Same as Database", "admin" });
		connectionPanel.add(cmbRestoreAuthSource, gbc);

		panel.add(connectionPanel, BorderLayout.NORTH);

		// Restore Options
		JPanel optionsPanel = new JPanel(new GridBagLayout());
		optionsPanel.setBorder(BorderFactory.createTitledBorder("Restore Options"));

		ButtonGroup group = new ButtonGroup();
		JRadioButton rbDropAndRestore = new JRadioButton("Drop existing collections and restore", true);
		JRadioButton rbReplaceOnly = new JRadioButton("Replace/Update only matching documents (by _id)");

		group.add(rbDropAndRestore);
		group.add(rbReplaceOnly);

		gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(5, 5, 5, 5);
		optionsPanel.add(rbDropAndRestore, gbc);

		gbc.gridy = 1;
		optionsPanel.add(rbReplaceOnly, gbc);

		// Add "Create if not exists" checkbox
		gbc.gridy = 2;
		JCheckBox chkCreateIfNotExists = new JCheckBox("Create database/user if not exists (requires no-auth MongoDB)");
		optionsPanel.add(chkCreateIfNotExists, gbc);

		panel.add(optionsPanel, BorderLayout.CENTER);

		// Restore Button
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		btnRestore = new JButton("Select Backup File & Restore");
		btnRestore.addActionListener(e -> {
			String authSource = cmbRestoreAuthSource.getSelectedIndex() == 0 ? txtRestoreDatabase.getText() : "admin";

			performRestore(txtRestoreHost.getText(), Integer.parseInt(txtRestorePort.getText()),
					txtRestoreDatabase.getText(), txtRestoreUsername.getText(),
					new String(txtRestorePassword.getPassword()), authSource, rbDropAndRestore.isSelected(),
					chkCreateIfNotExists.isSelected());
		});
		buttonPanel.add(btnRestore);

		panel.add(buttonPanel, BorderLayout.SOUTH);

		return panel;
	}

	/**
	 * Load collections from MongoDB
	 */
	private void loadCollections() {
		try {
			String authSource = cmbAuthSource.getSelectedIndex() == 0 ? txtDatabase.getText() : "admin";

			MongoConnectionConfig config = new MongoConnectionConfig(txtHost.getText(),
					Integer.parseInt(txtPort.getText()), txtDatabase.getText(), txtUsername.getText(),
					new String(txtPassword.getPassword()), authSource);

			// Show progress
			ProgressDialog progressDialog = new ProgressDialog(this, "Connecting");
			progressDialog.setMessage("Connecting to MongoDB...");

			btnConnect.setEnabled(false);

			new SwingWorker<List<String>, Void>() {
				@Override
				protected List<String> doInBackground() throws Exception {
					// Show progress dialog
					SwingUtilities.invokeLater(() -> progressDialog.showProgress());

					if (!connectionService.connect(config)) {
						throw new Exception("Failed to connect to MongoDB");
					}
					return connectionService.getCollectionNames();
				}

				@Override
				protected void done() {
					progressDialog.hideProgress();

					try {
						List<String> collections = get();

						// Clear table
						tableModel.setRowCount(0);
						chkSelectAll.setSelected(false);

						// Add collections
						for (String collection : collections) {
							long count = connectionService.getDatabase().getCollection(collection).countDocuments();
							tableModel.addRow(new Object[] { false, collection, count });
						}

						btnBackup.setEnabled(true);
						JOptionPane.showMessageDialog(MainFrame.this,
								"Connected successfully!\nFound " + collections.size() + " collections.", "Success",
								JOptionPane.INFORMATION_MESSAGE);

					} catch (Exception ex) {
						ex.printStackTrace();
						JOptionPane.showMessageDialog(MainFrame.this, "Connection failed:\n" + ex.getMessage(),
								"Error", JOptionPane.ERROR_MESSAGE);
					} finally {
						btnConnect.setEnabled(true);
					}
				}
			}.execute();

		} catch (NumberFormatException ex) {
			JOptionPane.showMessageDialog(this, "Invalid port number", "Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Perform backup
	 */
	private void performBackup() {
		// Get selected collections
		List<String> selectedCollections = new ArrayList<>();
		for (int i = 0; i < tableModel.getRowCount(); i++) {
			Boolean selected = (Boolean) tableModel.getValueAt(i, 0);
			if (selected != null && selected) {
				selectedCollections.add((String) tableModel.getValueAt(i, 1));
			}
		}

		if (selectedCollections.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Please select at least one collection", "Warning",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		// Choose save location
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Save Backup File");
		fileChooser.setSelectedFile(new File("mongodb_backup_" + System.currentTimeMillis() + ".zip"));

		if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
			File outputFile = fileChooser.getSelectedFile();

			// Ensure .zip extension
			if (!outputFile.getName().endsWith(".zip")) {
				outputFile = new File(outputFile.getAbsolutePath() + ".zip");
			}

			File finalOutputFile = outputFile;

			// Show progress dialog
			ProgressDialog progressDialog = new ProgressDialog(this, "Backup in Progress");
			progressDialog.setMessage("Backing up " + selectedCollections.size() + " collections...");

			btnBackup.setEnabled(false);

			new SwingWorker<Boolean, Void>() {
				@Override
				protected Boolean doInBackground() throws Exception {
					SwingUtilities.invokeLater(() -> progressDialog.showProgress());
					return backupRestoreService.backupCollections(selectedCollections, finalOutputFile);
				}

				@Override
				protected void done() {
					progressDialog.hideProgress();

					try {
						boolean success = get();
						if (success) {
							JOptionPane.showMessageDialog(MainFrame.this,
									"Backup completed successfully!\n\nFile saved to:\n" + finalOutputFile.getAbsolutePath()
											+ "\n\nCollections backed up: " + selectedCollections.size(),
									"Backup Successful", JOptionPane.INFORMATION_MESSAGE);
						} else {
							JOptionPane.showMessageDialog(MainFrame.this,
									"Backup failed!\nPlease check the console for details.", "Backup Failed",
									JOptionPane.ERROR_MESSAGE);
						}
					} catch (Exception ex) {
						ex.printStackTrace();
						JOptionPane.showMessageDialog(MainFrame.this, "Backup error:\n" + ex.getMessage(),
								"Backup Failed", JOptionPane.ERROR_MESSAGE);
					} finally {
						btnBackup.setEnabled(true);
					}
				}
			}.execute();
		}
	}

	/**
	 * Perform restore
	 */
	private void performRestore(String host, int port, String database, String username, String password,
			String authSource, boolean dropMode, boolean createIfNotExists) {

		// Choose backup file
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Select Backup File");
		fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
			@Override
			public boolean accept(File f) {
				return f.isDirectory() || f.getName().toLowerCase().endsWith(".zip");
			}

			@Override
			public String getDescription() {
				return "ZIP Files (*.zip)";
			}
		});

		if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}

		File backupFile = fileChooser.getSelectedFile();

		// First, get list of collections in backup
		ProgressDialog loadingDialog = new ProgressDialog(this, "Loading");
		loadingDialog.setMessage("Reading backup file...");

		new SwingWorker<List<String>, Void>() {
			@Override
			protected List<String> doInBackground() throws Exception {
				SwingUtilities.invokeLater(() -> loadingDialog.showProgress());
				return backupRestoreService.getCollectionsInBackup(backupFile);
			}

			@Override
			protected void done() {
				loadingDialog.hideProgress();

				try {
					List<String> collections = get();

					if (collections.isEmpty()) {
						JOptionPane.showMessageDialog(MainFrame.this, "No collections found in backup file!", "Error",
								JOptionPane.ERROR_MESSAGE);
						return;
					}

					// Show collection selection dialog
					CollectionSelectionDialog selectionDialog = new CollectionSelectionDialog(MainFrame.this,
							collections);
					selectionDialog.setVisible(true);

					if (selectionDialog.isApproved()) {
						List<String> selectedCollections = selectionDialog.getSelectedCollections();

						// Perform restore
						performActualRestore(host, port, database, username, password, authSource, backupFile,
								selectedCollections, dropMode, createIfNotExists);
					}

				} catch (Exception ex) {
					ex.printStackTrace();
					JOptionPane.showMessageDialog(MainFrame.this,
							"Failed to read backup file:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
				}
			}
		}.execute();
	}

	/**
	 * Perform actual restore operation
	 */
	private void performActualRestore(String host, int port, String database, String username, String password,
			String authSource, File backupFile, List<String> selectedCollections, boolean dropMode,
			boolean createIfNotExists) {

		MongoConnectionConfig config = new MongoConnectionConfig(host, port, database, username, password, authSource);

		ProgressDialog progressDialog = new ProgressDialog(this, "Restore in Progress");
		progressDialog.setMessage("Restoring " + selectedCollections.size() + " collections...");

		btnRestore.setEnabled(false);

		new SwingWorker<Boolean, Void>() {
			@Override
			protected Boolean doInBackground() throws Exception {
				SwingUtilities.invokeLater(() -> progressDialog.showProgress());

				if (!connectionService.connect(config, createIfNotExists)) {
					throw new Exception("Failed to connect to MongoDB");
				}

				return backupRestoreService.restoreCollections(backupFile, selectedCollections, dropMode);
			}

			@Override
			protected void done() {
				progressDialog.hideProgress();

				try {
					boolean success = get();
					if (success) {
						JOptionPane.showMessageDialog(MainFrame.this,
								"Restore completed successfully!\n\nDatabase: " + database + "\nCollections restored: "
										+ selectedCollections.size() + "\nMode: "
										+ (dropMode ? "Drop & Restore" : "Replace/Update"),
								"Restore Successful", JOptionPane.INFORMATION_MESSAGE);
					} else {
						JOptionPane.showMessageDialog(MainFrame.this,
								"Restore failed!\nPlease check the console for details.", "Restore Failed",
								JOptionPane.ERROR_MESSAGE);
					}
				} catch (Exception ex) {
					ex.printStackTrace();
					String errorMessage = ex.getMessage();

					if (errorMessage.contains("Authentication failed")) {
						errorMessage = "Authentication Failed!\n\n" + "Solutions:\n"
								+ "• Check 'Create database/user if not exists' option\n"
								+ "• Create the database/user manually\n"
								+ "• Leave username/password empty if no auth required";
					}

					JOptionPane.showMessageDialog(MainFrame.this, "Restore error:\n" + errorMessage, "Restore Failed",
							JOptionPane.ERROR_MESSAGE);
				} finally {
					btnRestore.setEnabled(true);
				}
			}
		}.execute();
	}
}