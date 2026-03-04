package com.seiri.backup_restore.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CollectionSelectionDialog extends JDialog {
    private JTable tblCollections;
    private DefaultTableModel tableModel;
    private JCheckBox chkSelectAll;
    private boolean approved = false;
    
    public CollectionSelectionDialog(Frame parent, List<String> collections) {
        super(parent, "Select Collections to Restore", true);
        initComponents(collections);
    }
    
    private void initComponents(List<String> collections) {
        setLayout(new BorderLayout(10, 10));
        setSize(500, 400);
        setLocationRelativeTo(getParent());
        
        // Title panel
        JPanel titlePanel = new JPanel();
        titlePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        JLabel titleLabel = new JLabel("Select collections to restore:");
        titleLabel.setFont(new Font(titleLabel.getFont().getName(), Font.BOLD, 14));
        titlePanel.add(titleLabel);
        add(titlePanel, BorderLayout.NORTH);
        
        // Table panel with Select All
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        
        // Select All checkbox
        JPanel selectAllPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        chkSelectAll = new JCheckBox("Select All");
        chkSelectAll.setSelected(true); // Default: select all
        chkSelectAll.addActionListener(e -> selectAll(chkSelectAll.isSelected()));
        selectAllPanel.add(chkSelectAll);
        tablePanel.add(selectAllPanel, BorderLayout.NORTH);
        
        // Collections table
        tableModel = new DefaultTableModel(new Object[]{"Select", "Collection Name"}, 0) {
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
        
        // Add collections to table
        for (String collection : collections) {
            tableModel.addRow(new Object[]{true, collection}); // Default: selected
        }
        
        tblCollections = new JTable(tableModel);
        tblCollections.setRowHeight(25);
        tblCollections.getColumnModel().getColumn(0).setMaxWidth(60);
        tblCollections.getColumnModel().getColumn(0).setPreferredWidth(60);
        
        JScrollPane scrollPane = new JScrollPane(tblCollections);
        tablePanel.add(scrollPane, BorderLayout.CENTER);
        
        add(tablePanel, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        
        JButton btnOk = new JButton("Restore Selected");
        btnOk.addActionListener(e -> {
            if (getSelectedCollections().isEmpty()) {
                JOptionPane.showMessageDialog(
                    this,
                    "Please select at least one collection to restore",
                    "Warning",
                    JOptionPane.WARNING_MESSAGE
                );
                return;
            }
            approved = true;
            dispose();
        });
        
        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> {
            approved = false;
            dispose();
        });
        
        buttonPanel.add(btnOk);
        buttonPanel.add(btnCancel);
        
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    private void selectAll(boolean select) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.setValueAt(select, i, 0);
        }
    }
    
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
    
    public List<String> getSelectedCollections() {
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Boolean isSelected = (Boolean) tableModel.getValueAt(i, 0);
            if (isSelected != null && isSelected) {
                selected.add((String) tableModel.getValueAt(i, 1));
            }
        }
        return selected;
    }
    
    public boolean isApproved() {
        return approved;
    }
}