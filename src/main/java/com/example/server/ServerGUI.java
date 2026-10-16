package com.example.server;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * ServerGUI: Giao diện quản trị Web Server hiện đại, trực quan.
 * Hiển thị Dashboard trạng thái, Log kết nối thời gian thực theo chuẩn Bài 12.
 */
public class ServerGUI extends JFrame {
    private WebServer server;
    private JTextField portField;
    private JButton startStopBtn, clearBtn;
    private JLabel statusBadge, requestCountLabel, uptimeLabel;
    private JTable logTable;
    private DefaultTableModel tableModel;

    private int totalRequests = 0;
    private long startTimeMs = 0;
    private Timer uptimeTimer;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    public ServerGUI() {
        setTitle("Java HTTP Web Server Dashboard (Port 8080)");
        setSize(850, 550);
        setMinimumSize(new Dimension(700, 450));
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        applyAppIcon();
        server = new WebServer(this);
        initUI();
    }

    private void applyAppIcon() {
        try {
            java.util.List<Image> icons = new java.util.ArrayList<>();
            int[] sizes = {16, 32, 64, 128, 256};
            for (int sz : sizes) {
                java.io.InputStream is = getClass().getResourceAsStream("/logo_" + sz + ".png");
                if (is != null) {
                    icons.add(javax.imageio.ImageIO.read(is));
                } else {
                    java.io.File f = new java.io.File("logo_" + sz + ".png");
                    if (f.exists()) {
                        icons.add(javax.imageio.ImageIO.read(f));
                    }
                }
            }
            if (!icons.isEmpty()) {
                setIconImages(icons);
            }
        } catch (Exception ignored) {}
    }

    private void initUI() {
        // Áp dụng Look and Feel hiện đại
        try {
            FlatLightLaf.setup();
        } catch (Exception ignored) {}

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Top Control Panel
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240)),
                new EmptyBorder(10, 15, 10, 15)
        ));
        topPanel.setBackground(Color.WHITE);

        // Bên trái: Port & Start/Stop
        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftControls.setOpaque(false);

        JLabel portLabel = new JLabel("Cổng (Port):");
        portLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        portField = new JTextField("8080", 6);
        portField.setFont(new Font("Segoe UI", Font.BOLD, 13));
        portField.setHorizontalAlignment(JTextField.CENTER);

        startStopBtn = new JButton("Khởi Động Server");
        startStopBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        startStopBtn.setBackground(new Color(16, 185, 129));
        startStopBtn.setForeground(Color.WHITE);
        startStopBtn.setFocusPainted(false);
        startStopBtn.addActionListener(e -> toggleServer());

        clearBtn = new JButton("Xóa Log");
        clearBtn.setFocusPainted(false);
        clearBtn.addActionListener(e -> {
            tableModel.setRowCount(0);
            totalRequests = 0;
            requestCountLabel.setText("Tổng Request: 0");
        });

        statusBadge = new JLabel("  ● ĐÃ DỪNG (STOPPED)  ");
        statusBadge.setOpaque(true);
        statusBadge.setBackground(new Color(254, 226, 226));
        statusBadge.setForeground(new Color(185, 28, 28));
        statusBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statusBadge.setBorder(BorderFactory.createLineBorder(new Color(252, 165, 165)));

        leftControls.add(portLabel);
        leftControls.add(portField);
        leftControls.add(startStopBtn);
        leftControls.add(clearBtn);
        leftControls.add(statusBadge);

        // Bên phải: Thống kê số lượng request & thời gian chạy
        JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        statsPanel.setOpaque(false);
        requestCountLabel = new JLabel("Tổng Request: 0");
        requestCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        uptimeLabel = new JLabel("Uptime: 00:00:00");
        uptimeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        statsPanel.add(requestCountLabel);
        statsPanel.add(uptimeLabel);

        topPanel.add(leftControls, BorderLayout.WEST);
        topPanel.add(statsPanel, BorderLayout.EAST);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Bảng Log Request chi tiết
        String[] columns = {"Thời gian", "Client IP", "Phương thức / Đường dẫn", "Mã trạng thái", "Kết quả"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        logTable = new JTable(tableModel);
        logTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        logTable.setRowHeight(26);
        logTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        logTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        logTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        logTable.getColumnModel().getColumn(2).setPreferredWidth(320);
        logTable.getColumnModel().getColumn(3).setPreferredWidth(90);
        logTable.getColumnModel().getColumn(4).setPreferredWidth(120);

        // Custom Renderer tô màu trạng thái Status Code
        logTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                c.setHorizontalAlignment(CENTER);
                c.setFont(new Font("Segoe UI", Font.BOLD, 12));
                if (value != null) {
                    int code = 0;
                    try { code = Integer.parseInt(value.toString()); } catch (Exception ignored) {}
                    if (code == 200 || code == 201) {
                        c.setForeground(new Color(22, 101, 52));
                    } else if (code == 404) {
                        c.setForeground(new Color(194, 65, 12));
                    } else if (code >= 400) {
                        c.setForeground(new Color(185, 28, 28));
                    }
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(logTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240)));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Footer hướng dẫn nhanh
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel tipLabel = new JLabel("💡 Mẹo: Hỗ trợ GET / (Web tĩnh), GET /api/time (JSON), POST /api/message (201 Created), GET 404 Not Found.");
        tipLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        tipLabel.setForeground(new Color(100, 116, 139));
        footer.add(tipLabel);
        mainPanel.add(footer, BorderLayout.SOUTH);

        add(mainPanel);

        // Timer cập nhật Uptime
        uptimeTimer = new Timer(1000, e -> updateUptime());
    }

    public void toggleServer() {
        if (server.isRunning()) {
            server.stop();
            startStopBtn.setText("Khởi Động Server");
            startStopBtn.setBackground(new Color(16, 185, 129));
            statusBadge.setText("  ● ĐÃ DỪNG (STOPPED)  ");
            statusBadge.setBackground(new Color(254, 226, 226));
            statusBadge.setForeground(new Color(185, 28, 28));
            portField.setEditable(true);
            uptimeTimer.stop();
        } else {
            try {
                int port = Integer.parseInt(portField.getText().trim());
                server.start(port);
                if (server.isRunning()) {
                    startStopBtn.setText("Dừng Server");
                    startStopBtn.setBackground(new Color(239, 68, 68));
                    statusBadge.setText("  ● ĐANG CHẠY (PORT " + port + ")  ");
                    statusBadge.setBackground(new Color(220, 252, 231));
                    statusBadge.setForeground(new Color(22, 101, 52));
                    portField.setEditable(false);
                    startTimeMs = System.currentTimeMillis();
                    uptimeTimer.start();
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Số cổng port không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void updateUptime() {
        if (startTimeMs > 0 && server.isRunning()) {
            long diff = (System.currentTimeMillis() - startTimeMs) / 1000;
            long hours = diff / 3600;
            long mins = (diff % 3600) / 60;
            long secs = diff % 60;
            uptimeLabel.setText(String.format("Uptime: %02d:%02d:%02d", hours, mins, secs));
        }
    }

    public void logStatus(String message) {
        SwingUtilities.invokeLater(() -> {
            tableModel.addRow(new Object[]{timeFormat.format(new Date()), "127.0.0.1", "[SYSTEM] " + message, "-", "INFO"});
            scrollToBottom();
        });
    }

    public void logRequest(String ip, String request, int statusCode) {
        SwingUtilities.invokeLater(() -> {
            totalRequests++;
            requestCountLabel.setText("Tổng Request: " + totalRequests);

            String result = "Thành công";
            if (statusCode == 201) result = "Created";
            else if (statusCode == 404) result = "Not Found";
            else if (statusCode == 415) result = "Wrong Type";
            else if (statusCode >= 400) result = "Lỗi Request";

            tableModel.addRow(new Object[]{timeFormat.format(new Date()), ip, request, statusCode, result});
            scrollToBottom();
        });
    }

    private void scrollToBottom() {
        if (logTable.getRowCount() > 0) {
            logTable.scrollRectToVisible(logTable.getCellRect(logTable.getRowCount() - 1, 0, true));
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ServerGUI().setVisible(true));
    }
}
