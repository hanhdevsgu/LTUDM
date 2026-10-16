package com.example.client;

import com.example.server.ServerGUI;
import com.formdev.flatlaf.FlatLightLaf;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * ClientGUI: Trình duyệt Web gọn gàng, thanh lịch theo phong cách Chrome hiện đại.
 * - Luôn tự động phóng to tối đa toàn màn hình (Maximized)
 * - Tối ưu thanh cuộn và giao diện không bị khoảng trống hay lề thừa
 * - Phím tắt toàn diện: F5 / Ctrl+R tải lại, Ctrl+T mở tab mới, Ctrl+W đóng tab, Ctrl+L / Alt+D chọn thanh địa chỉ
 */
public class ClientGUI extends JFrame {

    private JTabbedPane tabbedPane;
    private int tabCounter = 1;
    private ServerGUI serverGUI = null;

    public ClientGUI() {
        this(null);
    }

    public ClientGUI(ServerGUI serverGUI) {
        this.serverGUI = serverGUI;
        setTitle("SGU Web Browser");
        setSize(1200, 800);
        setMinimumSize(new Dimension(850, 550));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH); // Phóng to tối đa màn hình khi chạy

        applyAppIcon();
        initUI();
    }

    private void applyAppIcon() {
        try {
            List<Image> icons = new ArrayList<>();
            int[] sizes = {16, 32, 64, 128, 256};
            for (int sz : sizes) {
                InputStream is = getClass().getResourceAsStream("/logo_" + sz + ".png");
                if (is != null) {
                    icons.add(ImageIO.read(is));
                } else {
                    File f = new File("logo_" + sz + ".png");
                    if (f.exists()) {
                        icons.add(ImageIO.read(f));
                    }
                }
            }
            if (!icons.isEmpty()) {
                setIconImages(icons);
            }
        } catch (Exception ignored) {}
    }

    private void initUI() {
        try {
            FlatLightLaf.setup();
        } catch (Exception ignored) {}

        JPanel rootPanel = new JPanel(new BorderLayout(0, 0));
        rootPanel.setBackground(new Color(222, 225, 230));

        // Nút + Thêm Tab mới (sẽ gắn trực tiếp trên thanh tab header)
        JButton newTabBtn = new JButton("+");
        newTabBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        newTabBtn.setFocusPainted(false);
        newTabBtn.setContentAreaFilled(false);
        newTabBtn.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        newTabBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        newTabBtn.setToolTipText("Mở thẻ mới (Ctrl+T)");
        newTabBtn.addActionListener(e -> addNewTab("about:home"));

        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tabbedPane.setBackground(new Color(222, 225, 230));
        tabbedPane.setBorder(null);

        // Đặt nút + trên thanh tab header và loại bỏ hoàn toàn viền dư xung quanh nội dung web
        tabbedPane.putClientProperty("JTabbedPane.tabAreaInsets", new Insets(5, 6, 0, 6));
        tabbedPane.putClientProperty("JTabbedPane.hasFullBorder", false);
        tabbedPane.putClientProperty("JTabbedPane.showContentSeparator", false);
        tabbedPane.putClientProperty("JTabbedPane.trailingComponent", newTabBtn);

        // Đặt tabbedPane chiếm trọn vẹn 100% diện tích không bị thanh cạnh thừa
        rootPanel.add(tabbedPane, BorderLayout.CENTER);
        add(rootPanel);

        // Kích hoạt hệ thống phím tắt toàn cục (F5, Ctrl+T, Ctrl+W, Ctrl+R, Ctrl+L, Alt+D)
        setupGlobalShortcuts();

        // Khởi động sẵn thẻ Trang mới
        addNewTab("about:home");
    }

    private void setupGlobalShortcuts() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED) {
                return false;
            }

            Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            if (activeWindow != ClientGUI.this && !ClientGUI.this.isAncestorOf(activeWindow)) {
                return false;
            }

            int code = e.getKeyCode();
            boolean isCtrl = (e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0;
            boolean isAlt = (e.getModifiersEx() & KeyEvent.ALT_DOWN_MASK) != 0;

            // F5 hoặc Ctrl+R: Tải lại trang (Reload)
            if (code == KeyEvent.VK_F5 || (isCtrl && code == KeyEvent.VK_R)) {
                e.consume();
                SwingUtilities.invokeLater(ClientGUI.this::reloadActiveTab);
                return true;
            }

            // Ctrl+T: Mở thẻ mới
            if (isCtrl && code == KeyEvent.VK_T) {
                e.consume();
                SwingUtilities.invokeLater(() -> addNewTab("about:home"));
                return true;
            }

            // Ctrl+W: Đóng thẻ hiện tại
            if (isCtrl && code == KeyEvent.VK_W) {
                e.consume();
                SwingUtilities.invokeLater(ClientGUI.this::closeActiveTab);
                return true;
            }

            // Ctrl+L hoặc Alt+D: Focus vào thanh địa chỉ (Omnibox)
            if ((isCtrl && code == KeyEvent.VK_L) || (isAlt && code == KeyEvent.VK_D)) {
                e.consume();
                SwingUtilities.invokeLater(ClientGUI.this::focusActiveOmnibox);
                return true;
            }

            // Alt+Left: Quay lại (Back)
            if (isAlt && code == KeyEvent.VK_LEFT) {
                e.consume();
                SwingUtilities.invokeLater(ClientGUI.this::goBackActiveTab);
                return true;
            }

            // Alt+Right: Tiến lên (Forward)
            if (isAlt && code == KeyEvent.VK_RIGHT) {
                e.consume();
                SwingUtilities.invokeLater(ClientGUI.this::goForwardActiveTab);
                return true;
            }

            return false;
        });
    }

    private void reloadActiveTab() {
        Component comp = tabbedPane.getSelectedComponent();
        if (comp instanceof ChromeTabPanel) {
            ((ChromeTabPanel) comp).reload();
        }
    }

    private void closeActiveTab() {
        int idx = tabbedPane.getSelectedIndex();
        if (idx != -1) {
            if (tabbedPane.getTabCount() > 1) {
                tabbedPane.remove(idx);
            } else {
                Component comp = tabbedPane.getComponentAt(0);
                if (comp instanceof ChromeTabPanel) {
                    ((ChromeTabPanel) comp).navigate("about:home");
                }
            }
        }
    }

    private void focusActiveOmnibox() {
        Component comp = tabbedPane.getSelectedComponent();
        if (comp instanceof ChromeTabPanel) {
            ((ChromeTabPanel) comp).focusOmnibox();
        }
    }

    private void goBackActiveTab() {
        Component comp = tabbedPane.getSelectedComponent();
        if (comp instanceof ChromeTabPanel) {
            ((ChromeTabPanel) comp).goBack();
        }
    }

    private void goForwardActiveTab() {
        Component comp = tabbedPane.getSelectedComponent();
        if (comp instanceof ChromeTabPanel) {
            ((ChromeTabPanel) comp).goForward();
        }
    }

    private void addNewTab(String initialUrl) {
        ChromeTabPanel page = new ChromeTabPanel(initialUrl);
        String defaultTitle = "Trang mới";
        tabbedPane.addTab(defaultTitle, page);
        int tabIndex = tabbedPane.getTabCount() - 1;

        // Custom Tab Header
        JPanel tabHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        tabHeader.setOpaque(false);

        ImageIcon tabFavicon = null;
        try {
            InputStream is = getClass().getResourceAsStream("/logo_16.png");
            if (is != null) {
                tabFavicon = new ImageIcon(ImageIO.read(is));
            } else {
                File f = new File("logo_16.png");
                if (f.exists()) {
                    tabFavicon = new ImageIcon(ImageIO.read(f));
                }
            }
        } catch (Exception ignored) {}

        JLabel iconLabel;
        if (tabFavicon != null) {
            iconLabel = new JLabel(tabFavicon);
        } else {
            iconLabel = new JLabel("●");
            iconLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            iconLabel.setForeground(new Color(26, 115, 232));
        }

        JLabel titleLabel = new JLabel("Trang mới");
        titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        titleLabel.setPreferredSize(new Dimension(110, 18));

        JButton closeBtn = new JButton("×");
        closeBtn.setFont(new Font("Arial", Font.BOLD, 14));
        closeBtn.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        closeBtn.setContentAreaFilled(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeBtn.setToolTipText("Đóng thẻ");
        closeBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                closeBtn.setForeground(new Color(234, 67, 53));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                closeBtn.setForeground(Color.DARK_GRAY);
            }
        });
        closeBtn.addActionListener(e -> {
            int idx = tabbedPane.indexOfComponent(page);
            if (idx != -1) {
                if (tabbedPane.getTabCount() > 1) {
                    tabbedPane.remove(idx);
                } else {
                    page.navigate("about:home");
                }
            }
        });

        tabHeader.add(iconLabel);
        tabHeader.add(titleLabel);
        tabHeader.add(closeBtn);

        tabbedPane.setTabComponentAt(tabIndex, tabHeader);
        tabbedPane.setSelectedComponent(page);
    }

    private class ChromeTabPanel extends JPanel {
        private JTextField omniboxField;
        private JButton backBtn, forwardBtn, reloadBtn, homeBtn;
        private JLabel lockBadge;
        private JFXPanel jfxPanel;
        private WebView fxWebView;
        private WebEngine fxWebEngine;

        private Deque<String> backStack = new ArrayDeque<>();
        private Deque<String> forwardStack = new ArrayDeque<>();
        private String currentUrl = "";
        private volatile boolean isCrawling = false;

        public ChromeTabPanel(String startUrl) {
            setLayout(new BorderLayout());
            setBorder(null);
            initChromeUI(startUrl);
        }

        public void reload() {
            SwingUtilities.invokeLater(() -> navigate(omniboxField.getText().trim()));
        }

        public void focusOmnibox() {
            SwingUtilities.invokeLater(() -> {
                omniboxField.requestFocusInWindow();
                omniboxField.selectAll();
            });
        }

        private void initChromeUI(String startUrl) {
            JPanel navBar = new JPanel(new BorderLayout(8, 0));
            navBar.setBackground(Color.WHITE);
            navBar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(232, 234, 237)),
                    new EmptyBorder(6, 12, 6, 12)
            ));

            // Nút Back, Forward, Reload, Home
            JPanel leftNav = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            leftNav.setOpaque(false);

            backBtn = createToolbarButton("←", "Quay lại (Alt+←)");
            forwardBtn = createToolbarButton("→", "Tiến lên (Alt+→)");
            reloadBtn = createToolbarButton("↻", "Tải lại trang (F5 / Ctrl+R)");
            homeBtn = createToolbarButton("⌂", "Trang chủ");

            backBtn.setEnabled(false);
            forwardBtn.setEnabled(false);

            backBtn.addActionListener(e -> goBack());
            forwardBtn.addActionListener(e -> goForward());
            reloadBtn.addActionListener(e -> navigate(omniboxField.getText().trim()));
            homeBtn.addActionListener(e -> navigate("about:home"));

            leftNav.add(backBtn);
            leftNav.add(forwardBtn);
            leftNav.add(reloadBtn);
            leftNav.add(homeBtn);

            // Omnibox trung tâm: bo tròn thanh lịch
            JPanel omniboxContainer = new JPanel(new BorderLayout(8, 0));
            omniboxContainer.setBackground(new Color(241, 243, 244));
            omniboxContainer.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(218, 220, 224), 1, true),
                    new EmptyBorder(4, 10, 4, 10)
            ));

            lockBadge = new JLabel("[WEB]");
            lockBadge.setFont(new Font("Segoe UI", Font.BOLD, 10));
            lockBadge.setForeground(new Color(100, 116, 139));

            omniboxField = new JTextField(startUrl);
            omniboxField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            omniboxField.setBorder(null);
            omniboxField.setBackground(new Color(241, 243, 244));
            omniboxField.setToolTipText("Nhập URL (Ctrl+L hoặc Alt+D để chọn nhanh)");
            omniboxField.addActionListener(e -> navigate(omniboxField.getText().trim()));

            JButton goBtn = new JButton("Đi");
            goBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            goBtn.setForeground(new Color(26, 115, 232));
            goBtn.setContentAreaFilled(false);
            goBtn.setBorder(new EmptyBorder(2, 6, 2, 4));
            goBtn.setFocusPainted(false);
            goBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            goBtn.addActionListener(e -> navigate(omniboxField.getText().trim()));

            omniboxContainer.add(lockBadge, BorderLayout.WEST);
            omniboxContainer.add(omniboxField, BorderLayout.CENTER);
            omniboxContainer.add(goBtn, BorderLayout.EAST);

            // Bên phải: Nút Tải trang về máy chủ + Menu
            // Bên phải: Nút Tự Động, Thủ Công + Menu
            JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            rightActions.setOpaque(false);

            JButton autoBtn = new JButton("⚡ Tải Tự Động");
            autoBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            autoBtn.setBackground(new Color(237, 242, 247));
            autoBtn.setForeground(new Color(37, 99, 235));
            autoBtn.setFocusPainted(false);
            autoBtn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                    new EmptyBorder(4, 10, 4, 10)
            ));
            autoBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            autoBtn.setToolTipText("Tải tự động qua Socket từ URL trên thanh địa chỉ");
            autoBtn.addActionListener(e -> crawlAndSaveSource());

            JButton manualBtn = new JButton("✍ Thêm Thủ Công");
            manualBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            manualBtn.setBackground(new Color(241, 245, 249));
            manualBtn.setForeground(new Color(15, 118, 110));
            manualBtn.setFocusPainted(false);
            manualBtn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                    new EmptyBorder(4, 10, 4, 10)
            ));
            manualBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            manualBtn.setToolTipText("Thêm website thủ công bằng tay vào máy chủ IIS");
            manualBtn.addActionListener(e -> showManualAddDialog());

            JButton menuBtn = createToolbarButton("≡", "Tùy chọn");
            menuBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
            menuBtn.addActionListener(e -> showChromeMenu(menuBtn));

            rightActions.add(autoBtn);
            rightActions.add(manualBtn);
            rightActions.add(menuBtn);

            navBar.add(leftNav, BorderLayout.WEST);
            navBar.add(omniboxContainer, BorderLayout.CENTER);
            navBar.add(rightActions, BorderLayout.EAST);

            // CỖ MÁY WEBKIT WEBVENGINE (RENDER FRONT-END 100% DIỆN TÍCH)
            jfxPanel = new JFXPanel();
            jfxPanel.setBorder(null);
            Platform.runLater(() -> {
                fxWebView = new WebView();
                fxWebView.setFontScale(1.0);
                fxWebEngine = fxWebView.getEngine();
                fxWebEngine.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");

                fxWebEngine.locationProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal != null && newVal.contains("action:crawl")) {
                        Platform.runLater(() -> {
                            if (fxWebEngine != null) {
                                fxWebEngine.loadContent(buildDownloadingHtml(extractHost(currentUrl)), "text/html");
                            }
                        });
                        SwingUtilities.invokeLater(() -> crawlAndSaveSource());
                        return;
                    }
                    if (newVal != null && newVal.contains("action:manual_add")) {
                        SwingUtilities.invokeLater(this::showManualAddDialog);
                        return;
                    }
                    if (newVal != null && newVal.contains("action:open_folder")) {
                        SwingUtilities.invokeLater(this::openDownloadFolderInExplorer);
                        return;
                    }
                    if (newVal != null && newVal.contains("action:open_iis")) {
                        SwingUtilities.invokeLater(this::openIisFolderInExplorer);
                        return;
                    }
                    if (newVal != null && newVal.contains("action:delete?site=")) {
                        String target = newVal.substring(newVal.indexOf("action:delete?site=") + "action:delete?site=".length());
                        if (target.contains("&")) target = target.substring(0, target.indexOf('&'));
                        final String finalTarget = target;
                        SwingUtilities.invokeLater(() -> deleteSitePrompt(finalTarget));
                        return;
                    }
                    if (newVal != null && newVal.contains("?site=")) {
                        String target = newVal.substring(newVal.indexOf("?site=") + "?site=".length());
                        if (target.contains("&")) target = target.substring(0, target.indexOf('&'));
                        final String finalTarget = target;
                        SwingUtilities.invokeLater(() -> deployAndOpenLocalFolder(finalTarget));
                        return;
                    }
                    if (newVal != null && (newVal.startsWith("local:") || newVal.contains("local:"))) {
                        String target = newVal.substring(newVal.indexOf("local:") + "local:".length());
                        final String finalTarget = target;
                        SwingUtilities.invokeLater(() -> deployAndOpenLocalFolder(finalTarget));
                        return;
                    }
                    if (newVal != null && !newVal.isEmpty() && !newVal.startsWith("about:") && !newVal.startsWith("action:") && !newVal.startsWith("local:")) {
                        SwingUtilities.invokeLater(() -> {
                            omniboxField.setText(newVal);
                            currentUrl = newVal;
                            updateTabTitle(extractHost(newVal));
                        });
                    }
                });

                fxWebEngine.titleProperty().addListener((obs, oldVal, newTitle) -> {
                    if (newTitle != null && !newTitle.trim().isEmpty()) {
                        SwingUtilities.invokeLater(() -> updateTabTitle(newTitle.trim()));
                    }
                });

                Scene scene = new Scene(fxWebView);
                scene.setFill(javafx.scene.paint.Color.WHITE);

                // Đón bắt phím tắt ngay từ cấp độ JavaFX Scene (khi con trỏ đang ở trong trang web)
                scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                    javafx.scene.input.KeyCode code = event.getCode();
                    boolean isCtrl = event.isControlDown();
                    boolean isAlt = event.isAltDown();

                    if (code == javafx.scene.input.KeyCode.F5 || (isCtrl && code == javafx.scene.input.KeyCode.R)) {
                        event.consume();
                        SwingUtilities.invokeLater(this::reload);
                    } else if (isCtrl && code == javafx.scene.input.KeyCode.T) {
                        event.consume();
                        SwingUtilities.invokeLater(() -> addNewTab("about:home"));
                    } else if (isCtrl && code == javafx.scene.input.KeyCode.W) {
                        event.consume();
                        SwingUtilities.invokeLater(ClientGUI.this::closeActiveTab);
                    } else if ((isCtrl && code == javafx.scene.input.KeyCode.L) || (isAlt && code == javafx.scene.input.KeyCode.D)) {
                        event.consume();
                        SwingUtilities.invokeLater(this::focusOmnibox);
                    } else if (isAlt && code == javafx.scene.input.KeyCode.LEFT) {
                        event.consume();
                        SwingUtilities.invokeLater(this::goBack);
                    } else if (isAlt && code == javafx.scene.input.KeyCode.RIGHT) {
                        event.consume();
                        SwingUtilities.invokeLater(this::goForward);
                    }
                });

                jfxPanel.setScene(scene);

                if (startUrl != null && !startUrl.isEmpty()) {
                    SwingUtilities.invokeLater(() -> navigate(startUrl));
                }
            });

            add(navBar, BorderLayout.NORTH);
            add(jfxPanel, BorderLayout.CENTER);
        }

        private JButton createToolbarButton(String text, String tooltip) {
            JButton btn = new JButton(text);
            btn.setFont(new Font("Segoe UI Symbol", Font.BOLD, 15));
            btn.setFocusPainted(false);
            btn.setContentAreaFilled(false);
            btn.setBorder(new EmptyBorder(4, 8, 4, 8));
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.setToolTipText(tooltip);
            return btn;
        }

        private void showChromeMenu(Component invoker) {
            JPopupMenu menu = new JPopupMenu();

            JMenuItem itemNewTab = new JMenuItem("Thẻ mới (Ctrl+T)");
            itemNewTab.addActionListener(e -> addNewTab("about:home"));

            JMenuItem itemHome = new JMenuItem("Trang chủ");
            itemHome.addActionListener(e -> navigate("about:home"));

            JMenuItem itemReload = new JMenuItem("Tải lại (F5)");
            itemReload.addActionListener(e -> navigate(omniboxField.getText().trim()));

            JMenuItem itemAuto = new JMenuItem("⚡ Tải tự động qua Socket");
            itemAuto.addActionListener(e -> crawlAndSaveSource());

            JMenuItem itemManual = new JMenuItem("✍ Thêm website thủ công (Manual)");
            itemManual.addActionListener(e -> showManualAddDialog());

            JMenuItem itemOpenFolder = new JMenuItem("📂 Mở thư mục máy chủ (src_download)");
            itemOpenFolder.addActionListener(e -> openDownloadFolderInExplorer());

            JMenuItem itemOpenIisFolder = new JMenuItem("🌐 Mở thư mục IIS (C:\\inetpub\\site1)");
            itemOpenIisFolder.addActionListener(e -> openIisFolderInExplorer());

            JMenuItem itemDelete = new JMenuItem("🗑️ Quản lý & Xóa website khỏi máy chủ...");
            itemDelete.addActionListener(e -> showDeleteWebsiteMenu());

            menu.add(itemNewTab);
            menu.add(itemHome);
            menu.add(itemReload);
            menu.addSeparator();
            menu.add(itemAuto);
            menu.add(itemManual);
            menu.add(itemOpenFolder);
            menu.add(itemOpenIisFolder);
            menu.addSeparator();
            menu.add(itemDelete);

            menu.show(invoker, 0, invoker.getHeight());
        }

        private void showManualAddDialog() {
            String[] options = {
                    "1. Tự tạo trang web mới (Nhập mã HTML)",
                    "2. Chọn thư mục web có sẵn từ máy tính",
                    "3. Mở thư mục src_download để chép file bằng tay",
                    "Hủy"
            };
            int choice = JOptionPane.showOptionDialog(ClientGUI.this,
                    "Chọn phương thức thêm website thủ công vào IIS:\n\n" +
                    "• Chế độ Thủ Công giúp bạn tự tay thêm và quản trị website trên máy chủ IIS mà không cần cào tự động.",
                    "Thêm Website Thủ Công (Manual Mode)",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

            if (choice == 0) {
                createCustomWebPageDialog();
            } else if (choice == 1) {
                importFolderDialog();
            } else if (choice == 2) {
                openDownloadFolderInExplorer();
            }
        }

        private void createCustomWebPageDialog() {
            JPanel panel = new JPanel(new BorderLayout(5, 8));

            JPanel fieldsPanel = new JPanel(new GridLayout(2, 2, 8, 8));
            JTextField titleField = new JTextField("Website Thủ Công");
            JTextField folderField = new JTextField("demo");
            fieldsPanel.add(new JLabel("Tên hiển thị website:"));
            fieldsPanel.add(titleField);
            fieldsPanel.add(new JLabel("Tên thư mục (ví dụ: demo -> src_demo):"));
            fieldsPanel.add(folderField);

            JTextArea htmlArea = new JTextArea(10, 42);
            htmlArea.setText("<!DOCTYPE html>\n<html>\n<head>\n    <meta charset=\"utf-8\">\n    <title>Website Thủ Công - IIS Server</title>\n    <style>\n        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f8fafc; color: #1e293b; text-align: center; padding: 60px 20px; }\n        .box { max-width: 600px; margin: 0 auto; background: #ffffff; padding: 40px; border-radius: 16px; box-shadow: 0 4px 20px rgba(0,0,0,0.06); border: 1px solid #e2e8f0; }\n        h1 { color: #0f766e; font-size: 26px; margin-bottom: 12px; }\n        p { font-size: 15px; color: #475569; line-height: 1.6; }\n        .badge { display: inline-block; background: #ccfbf1; color: #0f766e; padding: 6px 14px; border-radius: 20px; font-weight: bold; font-size: 13px; margin-top: 16px; }\n    </style>\n</head>\n<body>\n    <div class=\"box\">\n        <h1>Website Được Tạo Thủ Công Trên IIS</h1>\n        <p>Trang web này được cấu hình trực tiếp bằng tay, lưu trữ vào thư mục máy chủ <code>src_download/</code> và đồng bộ tới máy chủ IIS thành công!</p>\n        <div class=\"badge\">Mã Nguồn Cục Bộ - Đồ Án Lập Trình Mạng SGU</div>\n    </div>\n</body>\n</html>");
            htmlArea.setFont(new Font("Consolas", Font.PLAIN, 12));

            panel.add(fieldsPanel, BorderLayout.NORTH);
            panel.add(new JScrollPane(htmlArea), BorderLayout.CENTER);

            int result = JOptionPane.showConfirmDialog(ClientGUI.this, panel,
                    "Tạo Website Thủ Công Cho IIS", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

            if (result == JOptionPane.OK_OPTION) {
                String folderName = folderField.getText().trim();
                if (folderName.isEmpty()) folderName = "custom";
                if (!folderName.startsWith("src_")) folderName = "src_" + folderName;

                File targetDir = new File("src_download", folderName);
                targetDir.mkdirs();

                File indexFile = new File(targetDir, "index.html");
                try {
                    java.nio.file.Files.writeString(indexFile.toPath(), htmlArea.getText(), java.nio.charset.StandardCharsets.UTF_8);

                    // Đồng bộ sang IIS C:\inetpub\site1 và www
                    WebClient.copyAllFiles(targetDir, new File("C:\\inetpub\\site1"));
                    WebClient.copyAllFiles(targetDir, new File("www"));

                    JOptionPane.showMessageDialog(ClientGUI.this,
                            "Đã tạo website thủ công thành công tại: " + targetDir.getPath() + "\nĐã đồng bộ sang máy chủ IIS C:\\inetpub\\site1\\",
                            "Thành Công", JOptionPane.INFORMATION_MESSAGE);

                    deployAndOpenLocalFolder(folderName);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ClientGUI.this,
                            "Lỗi khi lưu website: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        private void importFolderDialog() {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Chọn thư mục chứa website (phải có file index.html)");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            int res = chooser.showOpenDialog(ClientGUI.this);
            if (res == JFileChooser.APPROVE_OPTION) {
                File selectedDir = chooser.getSelectedFile();
                if (selectedDir != null && selectedDir.isDirectory()) {
                    File indexFile = new File(selectedDir, "index.html");
                    if (!indexFile.exists()) {
                        JOptionPane.showMessageDialog(ClientGUI.this,
                                "Thư mục bạn chọn không chứa file index.html!",
                                "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    String folderName = selectedDir.getName();
                    if (!folderName.startsWith("src_")) folderName = "src_" + folderName;

                    File targetDir = new File("src_download", folderName);
                    targetDir.mkdirs();
                    WebClient.copyAllFiles(selectedDir, targetDir);

                    // Đồng bộ sang IIS
                    WebClient.copyAllFiles(targetDir, new File("C:\\inetpub\\site1"));
                    WebClient.copyAllFiles(targetDir, new File("www"));

                    JOptionPane.showMessageDialog(ClientGUI.this,
                            "Đã thêm website thành công vào: " + targetDir.getPath() + "\nĐã đồng bộ sang máy chủ IIS C:\\inetpub\\site1\\",
                            "Thành Công", JOptionPane.INFORMATION_MESSAGE);

                    deployAndOpenLocalFolder(folderName);
                }
            }
        }

        private void openDownloadFolderInExplorer() {
            try {
                File dir = new File("src_download");
                if (!dir.exists()) dir.mkdirs();
                Desktop.getDesktop().open(dir);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(ClientGUI.this, "Không thể mở thư mục: " + ex.getMessage());
            }
        }

        private void openIisFolderInExplorer() {
            try {
                File dir = new File("C:\\inetpub\\site1");
                if (!dir.exists()) dir.mkdirs();
                Desktop.getDesktop().open(dir);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(ClientGUI.this, "Không thể mở thư mục IIS: " + ex.getMessage());
            }
        }

        private void deleteSitePrompt(String folderName) {
            String domain = folderName.startsWith("src_") ? folderName.substring(4) : folderName;
            String title = formatDomainTitle(domain);
            int choice = JOptionPane.showConfirmDialog(ClientGUI.this,
                    "Bạn có chắc chắn muốn xóa website \"" + title + "\" (" + folderName + ") khỏi máy chủ IIS và thư mục src_download/ không?",
                    "Xác Nhận Xóa Website",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                File dir = new File("src_download", folderName);
                if (dir.exists()) {
                    deleteDirectoryRecursively(dir);
                }
                JOptionPane.showMessageDialog(ClientGUI.this,
                        "Đã xóa thành công website: " + title,
                        "Đã Xóa", JOptionPane.INFORMATION_MESSAGE);
                navigate("about:home");
            }
        }

        private void deleteDirectoryRecursively(File file) {
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteDirectoryRecursively(child);
                    }
                }
            }
            file.delete();
        }

        private void showDeleteWebsiteMenu() {
            List<DownloadedSite> sites = scanDownloadedSites();
            if (sites.isEmpty()) {
                JOptionPane.showMessageDialog(ClientGUI.this,
                        "Hiện chưa có website nào trong thư mục src_download/ để xóa.",
                        "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String[] siteNames = new String[sites.size()];
            for (int i = 0; i < sites.size(); i++) {
                siteNames[i] = sites.get(i).title + " (" + sites.get(i).folderName + ")";
            }
            String selected = (String) JOptionPane.showInputDialog(ClientGUI.this,
                    "Chọn website bạn muốn xóa khỏi máy chủ:",
                    "Xóa Website Khỏi Máy Chủ",
                    JOptionPane.QUESTION_MESSAGE, null, siteNames, siteNames[0]);
            if (selected != null) {
                for (DownloadedSite s : sites) {
                    if (selected.contains(s.folderName)) {
                        deleteSitePrompt(s.folderName);
                        break;
                    }
                }
            }
        }

        private void crawlAndSaveSource() {
            if (isCrawling) return;

            String url = omniboxField.getText().trim();
            if (url.isEmpty() || url.startsWith("about:")) {
                String input = JOptionPane.showInputDialog(this,
                        "Nhập URL website bạn muốn tải mã nguồn (ví dụ: https://www.24h.com.vn hoặc https://google.com):",
                        "Tải Mã Nguồn Website", JOptionPane.QUESTION_MESSAGE);
                if (input != null && !input.trim().isEmpty()) {
                    url = input.trim();
                    omniboxField.setText(url);
                } else {
                    return;
                }
            }

            if (url.contains("localhost") || url.contains("127.0.0.1")) {
                JOptionPane.showMessageDialog(this, "Không cần tải máy chủ nội bộ Localhost!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
                omniboxField.setText(url);
            }

            String checkFolder = WebClient.extractDomainFolderName(url);
            File localDir = new File("src_download", checkFolder);
            File localFile = new File(localDir, "index.html");
            if (localFile.exists() && localFile.length() > 0) {
                // Đã có sẵn trên máy chủ! Tự động nạp và mở ngay
                deployAndOpenLocalFolder(checkFolder);
                return;
            }

            final String targetUrl = url;
            final int port = 8443;
            final String host = extractHost(targetUrl);

            isCrawling = true;

            // HIỂN THỊ NGAY LẬP TỨC MÀN HÌNH ĐANG TẢI (Không để màn hình cũ đơ gây cảm giác lag)
            Platform.runLater(() -> {
                if (fxWebEngine != null) {
                    fxWebEngine.loadContent(buildDownloadingHtml(host), "text/html");
                }
            });
            updateTabTitle("Đang tải " + host);

            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override
                protected String doInBackground() throws Exception {
                    return WebClient.crawlAndDeploy(targetUrl, port);
                }

                @Override
                protected void done() {
                    isCrawling = false;
                    try {
                        String folderName = get();
                        // TỰ ĐỘNG MỞ TRANG WEB VỚI URL THỰC TẾ
                        deployAndOpenLocalFolder(folderName);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(ClientGUI.this,
                                "Lỗi khi lấy mã nguồn URL: " + ex.getMessage(),
                                "Lỗi", JOptionPane.ERROR_MESSAGE);
                        String notCachedHtml = buildNotCachedHtml(host, targetUrl);
                        Platform.runLater(() -> {
                            if (fxWebEngine != null) {
                                fxWebEngine.loadContent(notCachedHtml, "text/html");
                            }
                        });
                        updateTabTitle(host);
                    }
                }
            };
            worker.execute();
        }

        public void navigate(String urlString) {
            if (urlString == null || urlString.trim().isEmpty()) return;
            urlString = urlString.trim();

            if (urlString.equalsIgnoreCase("about:home") || urlString.equalsIgnoreCase("about:blank")) {
                currentUrl = "about:home";
                omniboxField.setText("");
                lockBadge.setText("[TRANG CHỦ]");
                lockBadge.setForeground(new Color(100, 116, 139));

                String homeHtml = buildHomeHtml();

                Platform.runLater(() -> {
                    if (fxWebEngine != null) {
                        fxWebEngine.loadContent(homeHtml, "text/html");
                    }
                });
                updateTabTitle("Trang mới");
                return;
            }

            if (urlString.startsWith("local:")) {
                String fName = urlString.substring("local:".length()).trim();
                deployAndOpenLocalFolder(fName);
                return;
            }

            if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                urlString = "https://" + urlString;
            }

            omniboxField.setText(urlString);

            if (!currentUrl.isEmpty() && !currentUrl.equalsIgnoreCase(urlString)) {
                backStack.push(currentUrl);
                forwardStack.clear();
                backBtn.setEnabled(true);
                forwardBtn.setEnabled(false);
            }
            currentUrl = urlString;

            boolean isHttps = urlString.toLowerCase().startsWith("https://");
            if (isHttps) {
                lockBadge.setText("[HTTPS]");
                lockBadge.setForeground(new Color(24, 128, 56));
            } else {
                lockBadge.setText("[HTTP]");
                lockBadge.setForeground(new Color(234, 67, 53));
            }

            boolean isLocal = urlString.contains("localhost") || urlString.contains("127.0.0.1");
            if (!isLocal) {
                // Kiểm tra xem trang web này đã được tải về máy chủ cục bộ chưa
                String folderName = WebClient.extractDomainFolderName(urlString);
                File localDir = new File("src_download", folderName);
                File localFile = new File(localDir, "index.html");

                if (localFile.exists() && localFile.length() > 0) {
                    // Đã có sẵn trên máy chủ! Tự động nạp toàn bộ mã nguồn vào IIS và hiển thị
                    deployAndOpenLocalFolder(folderName);
                    return;
                }

                // Chưa có trên máy chủ: Hỏi người dùng có muốn tải về ngay không!
                String host = extractHost(urlString);
                int choice = JOptionPane.showConfirmDialog(ClientGUI.this,
                        "Trang web này (" + host + ") chưa có bản lưu trên máy chủ cục bộ.\n\n" +
                        "Bạn có muốn tải toàn bộ mã nguồn về máy chủ ngay bây giờ không?",
                        "Yêu Cầu Tải Trang Web",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);

                if (choice == JOptionPane.YES_OPTION) {
                    // Người dùng bấm Đồng Ý -> Tải ngay và tải xong tự chuyển trang
                    crawlAndSaveSource();
                    return;
                }

                // Nếu người dùng chọn Không: hiện màn hình thông báo
                String notCachedHtml = buildNotCachedHtml(host, urlString);
                Platform.runLater(() -> {
                    if (fxWebEngine != null) {
                        fxWebEngine.loadContent(notCachedHtml, "text/html");
                    }
                });
                updateTabTitle(host);
                return;
            }

            final String reqUrl = urlString;

            if (!isLocal) {
                Platform.runLater(() -> {
                    if (fxWebEngine != null) {
                        fxWebEngine.loadContent(
                                "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
                                "<style>" +
                                "body { display: flex; align-items: center; justify-content: center; height: 85vh; margin: 0; background: #ffffff; }" +
                                ".spinner { width: 38px; height: 38px; border: 3px solid #f1f3f4; border-top: 3px solid #1a73e8; border-radius: 50%; animation: spin 0.8s linear infinite; }" +
                                "@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }" +
                                "</style></head><body>" +
                                "<div class='spinner'></div>" +
                                "</body></html>",
                                "text/html"
                        );
                    }
                });
            }

            SwingWorker<WebClient.HttpResponse, Void> worker = new SwingWorker<>() {
                @Override
                protected WebClient.HttpResponse doInBackground() throws Exception {
                    return WebClient.sendGetRequest(reqUrl);
                }

                @Override
                protected void done() {
                    try {
                        WebClient.HttpResponse res = get();
                        final String bodyContent = res.body;

                        Platform.runLater(() -> {
                            if (fxWebEngine != null) {
                                String htmlWithBase = injectBaseHref(bodyContent, reqUrl);
                                fxWebEngine.loadContent(htmlWithBase, "text/html");
                            }
                        });

                        updateTabTitle(extractHost(reqUrl));

                    } catch (Exception ex) {
                        String msg = ex.getMessage();
                        String errorHtml = "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
                                "<style>" +
                                "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; padding: 60px 40px; max-width: 650px; margin: 0 auto; color: #202124; }" +
                                "h2 { color: #d93025; font-size: 20px; font-weight: 500; margin-bottom: 12px; }" +
                                "p { color: #5f6368; font-size: 14px; line-height: 1.6; }" +
                                ".box { background: #f8f9fa; border: 1px solid #dadce0; border-radius: 8px; padding: 14px; margin: 20px 0; font-family: monospace; font-size: 13px; color: #3c4043; }" +
                                "</style></head><body>" +
                                "<h2>Không thể truy cập trang web này</h2>" +
                                "<p>Không thể thiết lập kết nối tới máy chủ.</p>" +
                                "<div class='box'><b>URL:</b> " + reqUrl + "<br/><b>Lỗi:</b> " + msg + "</div>" +
                                "</body></html>";

                        Platform.runLater(() -> {
                            if (fxWebEngine != null) {
                                fxWebEngine.loadContent(errorHtml, "text/html");
                            }
                        });
                        updateTabTitle("Lỗi kết nối");
                    }
                }
            };
            worker.execute();
        }

        private String injectBaseHref(String html, String baseUrl) {
            if (html == null) return "";
            if (html.toLowerCase().contains("<base ") || html.toLowerCase().contains("<base>")) {
                return html;
            }
            String baseTag = "<base href=\"" + baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "\">";
            if (html.toLowerCase().contains("<head>")) {
                return html.replaceFirst("(?i)<head>", "<head>\n    " + baseTag);
            } else {
                return baseTag + "\n" + html;
            }
        }

        private String buildAlreadyCachedHtml(String domain, String folderName) {
            return "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
                    "<style>" +
                    "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #ffffff; color: #1e293b; margin: 0; padding: 80px 20px; text-align: center; }" +
                    ".card { max-width: 520px; margin: 0 auto; background: #f8fafc; border: 1px solid #dcfce7; border-radius: 16px; padding: 36px 28px; box-shadow: 0 4px 14px rgba(0,0,0,0.03); }" +
                    ".icon { width: 50px; height: 50px; border-radius: 50%; background: #dcfce7; color: #15803d; display: inline-flex; align-items: center; justify-content: center; margin-bottom: 16px; font-size: 16px; font-weight: bold; }" +
                    "h2 { font-size: 20px; font-weight: 700; color: #166534; margin: 0 0 10px 0; }" +
                    "p { font-size: 14px; color: #475569; line-height: 1.6; margin: 0 0 16px 0; }" +
                    ".info { background: #ffffff; border: 1px solid #bbf7d0; border-radius: 8px; padding: 10px 14px; font-size: 13px; color: #15803d; font-weight: 500; display: inline-block; }" +
                    "</style></head><body>" +
                    "<div class='card'>" +
                    "<div class='icon'>OK</div>" +
                    "<h2>Website đã có sẵn trên máy chủ nội bộ!</h2>" +
                    "<p>Hệ thống tìm thấy bản lưu trong thư mục <b>" + folderName + "\\</b>.<br/>Đang tự động chuyển hướng...</p>" +
                    "<div class='info'>Đang tải website...</div>" +
                    "</div>" +
                    "</body></html>";
        }

        private String buildDownloadingHtml(String domain) {
            return "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
                    "<style>" +
                    "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #ffffff; color: #1e293b; margin: 0; padding: 100px 20px; text-align: center; }" +
                    ".card { max-width: 520px; margin: 0 auto; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 16px; padding: 40px 30px; box-shadow: 0 4px 14px rgba(0,0,0,0.03); }" +
                    ".spinner { width: 44px; height: 44px; border: 3px solid #e2e8f0; border-top: 3px solid #2563eb; border-radius: 50%; animation: spin 0.8s linear infinite; margin: 0 auto 20px auto; }" +
                    "@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }" +
                    "h2 { font-size: 20px; font-weight: 700; color: #0f172a; margin: 0 0 10px 0; }" +
                    "p { font-size: 14px; color: #64748b; line-height: 1.6; margin: 0; }" +
                    ".badge { display: inline-block; background: #eff6ff; color: #2563eb; padding: 6px 14px; border-radius: 20px; font-size: 13px; font-weight: 600; margin-top: 18px; }" +
                    "</style></head><body>" +
                    "<div class='card'>" +
                    "<div class='spinner'></div>" +
                    "<h2>Đang tải toàn bộ mã nguồn về máy chủ...</h2>" +
                    "<p>Đang crawl HTML, CSS, JavaScript cho website: <b>" + domain + "</b><br/>Hệ thống sẽ tự động hiển thị trang ngay khi tải xong.</p>" +
                    "<div class='badge'>Lưu vào src_download/</div>" +
                    "</div>" +
                    "</body></html>";
        }

        private String buildNotCachedHtml(String domain, String fullUrl) {
            return "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
                    "<style>" +
                    "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #ffffff; color: #1e293b; margin: 0; padding: 60px 20px; text-align: center; }" +
                    ".card { max-width: 560px; margin: 0 auto; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 16px; padding: 36px 30px; box-shadow: 0 4px 14px rgba(0,0,0,0.03); }" +
                    ".icon { width: 52px; height: 52px; border-radius: 50%; background: #eff6ff; display: inline-flex; align-items: center; justify-content: center; margin-bottom: 20px; }" +
                    "h2 { font-size: 21px; font-weight: 700; color: #0f172a; margin: 0 0 12px 0; }" +
                    "p { font-size: 14px; color: #64748b; line-height: 1.6; margin: 0 0 20px 0; }" +
                    ".info-box { background: #ffffff; border: 1px solid #cbd5e1; border-radius: 10px; padding: 14px 18px; margin: 0 0 24px 0; text-align: left; font-size: 13px; color: #334155; line-height: 1.7; }" +
                    ".info-box b { color: #0f172a; }" +
                    ".btn-download { display: inline-block; background: #2563eb; color: #ffffff; text-decoration: none; padding: 12px 26px; border-radius: 8px; font-size: 14px; font-weight: 600; box-shadow: 0 2px 6px rgba(37,99,235,0.25); transition: background 0.2s; cursor: pointer; }" +
                    ".btn-download:hover { background: #1d4ed8; }" +
                    "</style></head><body>" +
                    "<div class='card'>" +
                    "<div class='icon'>" +
                    "<svg width='26' height='26' viewBox='0 0 24 24' fill='none' stroke='#2563eb' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'>" +
                    "<circle cx='12' cy='12' r='10'></circle>" +
                    "<line x1='12' y1='8' x2='12' y2='12'></line>" +
                    "<line x1='12' y1='16' x2='12.01' y2='16'></line>" +
                    "</svg>" +
                    "</div>" +
                    "<h2>Trang web chưa được lưu trữ trên máy chủ cục bộ</h2>" +
                    "<p>Bạn chưa tải bản lưu của website này về máy chủ nội bộ. Để kiểm soát và đảm bảo an toàn mạng, hệ thống chỉ hiển thị các trang web đã được đồng bộ mã nguồn về máy chủ cục bộ.</p>" +
                    "<div class='info-box'>" +
                    "<b>Địa chỉ yêu cầu:</b> " + domain + "<br/>" +
                    "<b>Trạng thái:</b> Chưa có bản lưu trên máy chủ cục bộ (Localhost)<br/>" +
                    "<b>Hướng dẫn:</b> Nhấn nút bên dưới để tải mã nguồn trang này về máy chủ trước khi xem." +
                    "</div>" +
                    "<a href='action:crawl' class='btn-download' onclick='this.innerText=\"Đang chuẩn bị tải...\"; this.style.opacity=\"0.6\";'>Tải trang này về máy chủ ngay</a>" +
                    "</div>" +
                    "</body></html>";
        }

        private String getLogoBase64() {
            try {
                InputStream is = getClass().getResourceAsStream("/logo_128.png");
                byte[] bytes = null;
                if (is != null) {
                    bytes = is.readAllBytes();
                } else {
                    File f = new File("logo_128.png");
                    if (f.exists()) {
                        bytes = java.nio.file.Files.readAllBytes(f.toPath());
                    }
                }
                if (bytes != null && bytes.length > 0) {
                    return java.util.Base64.getEncoder().encodeToString(bytes);
                }
            } catch (Exception ignored) {}
            return "";
        }

        private String buildHomeHtml() {
            String logoBase64 = getLogoBase64();
            String logoHtml;
            if (!logoBase64.isEmpty()) {
                logoHtml = "<img src='data:image/png;base64," + logoBase64 + "' width='88' height='88' style='border-radius:24px; box-shadow: 0 8px 24px rgba(37,99,235,0.22); margin-bottom: 12px;' alt='Logo'>";
            } else {
                logoHtml = "<svg width='56' height='56' viewBox='0 0 24 24' fill='none' stroke='#2563eb' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'>" +
                        "<circle cx='12' cy='12' r='10'></circle>" +
                        "<line x1='2' y1='12' x2='22' y2='12'></line>" +
                        "<path d='M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z'></path>" +
                        "</svg>";
            }

            List<DownloadedSite> sites = scanDownloadedSites();
            StringBuilder shortcutsHtml = new StringBuilder();

            shortcutsHtml.append("<div class='action-bar'>")
                    .append("<a href='action:manual_add' class='btn-action btn-primary'>+ Thêm Website Thủ Công (Manual)</a>")
                    .append("<a href='action:open_folder' class='btn-action btn-secondary'>📂 Mở thư mục src_download</a>")
                    .append("<a href='action:open_iis' class='btn-action btn-secondary'>🌐 Mở thư mục IIS (C:\\inetpub\\site1)</a>")
                    .append("</div>");

            if (sites.isEmpty()) {
                shortcutsHtml.append("<div style='background:#f8fafc; border:1px dashed #cbd5e1; border-radius:12px; padding:24px 30px; max-width:480px; margin:0 auto; color:#64748b; font-size:13px; line-height:1.6;'>")
                        .append("<b style='color:#334155;'>Chưa có website nào trong thư mục src_download/</b><br/>")
                        .append("Bạn có thể bấm <b>⚡ Tải Tự Động</b> từ URL trên thanh địa chỉ hoặc bấm <b>+ Thêm Website Thủ Công</b>.")
                        .append("</div>");
            } else {
                shortcutsHtml.append("<div style='font-size:12px; font-weight:700; color:#475569; margin-bottom:16px; text-transform:uppercase; letter-spacing:0.5px;'>Các website trên máy chủ IIS (Nhấn để mở • Rê chuột để xóa):</div>");
                shortcutsHtml.append("<div class='shortcuts'>");
                for (DownloadedSite s : sites) {
                    shortcutsHtml.append("<div class='shortcut-card'>")
                            .append("<a href='https://localhost:8443/?site=").append(s.folderName).append("' class='shortcut-item'>")
                            .append("<div class='shortcut-icon' style='background:").append(s.bg).append("; color:").append(s.color).append(";'>").append(s.badge).append("</div>")
                            .append("<div class='shortcut-title'>").append(s.title).append("</div>")
                            .append("</a>")
                            .append("<a href='action:delete?site=").append(s.folderName).append("' class='shortcut-del' title='Xóa website này khỏi máy chủ IIS'>×</a>")
                            .append("</div>");
                }
                shortcutsHtml.append("</div>");
            }

            return "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
                    "<style>" +
                    "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background: #ffffff; margin: 0; padding: 50px 20px 40px; text-align: center; color: #1e293b; }" +
                    ".icon-wrapper { margin-bottom: 12px; }" +
                    ".title { font-size: 30px; font-weight: 700; color: #0f172a; margin: 0 0 6px 0; letter-spacing: -0.5px; }" +
                    ".subtitle { font-size: 14px; color: #64748b; margin: 0 0 25px 0; }" +
                    ".action-bar { display: flex; justify-content: center; gap: 10px; margin-bottom: 30px; flex-wrap: wrap; }" +
                    ".btn-action { text-decoration: none; padding: 8px 16px; border-radius: 8px; font-size: 13px; font-weight: 600; display: inline-flex; align-items: center; transition: all 0.2s; }" +
                    ".btn-primary { background: #2563eb; color: #ffffff; box-shadow: 0 2px 6px rgba(37,99,235,0.25); }" +
                    ".btn-primary:hover { background: #1d4ed8; transform: translateY(-1px); }" +
                    ".btn-secondary { background: #f8fafc; color: #334155; border: 1px solid #cbd5e1; }" +
                    ".btn-secondary:hover { background: #f1f5f9; border-color: #94a3b8; }" +
                    ".shortcuts { display: flex; justify-content: center; gap: 16px; flex-wrap: wrap; max-width: 680px; margin: 0 auto; }" +
                    ".shortcut-card { position: relative; display: inline-block; }" +
                    ".shortcut-item { display: flex; flex-direction: column; align-items: center; text-decoration: none; width: 110px; padding: 12px 10px; border-radius: 12px; transition: all 0.2s; }" +
                    ".shortcut-item:hover { background-color: #f1f5f9; transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.05); }" +
                    ".shortcut-del { position: absolute; top: -2px; right: 2px; width: 22px; height: 22px; border-radius: 50%; background: #fee2e2; color: #dc2626; font-size: 14px; font-weight: bold; line-height: 20px; text-align: center; text-decoration: none; opacity: 0; transition: all 0.2s; box-shadow: 0 2px 5px rgba(0,0,0,0.15); z-index: 10; border: 1px solid #fca5a5; }" +
                    ".shortcut-card:hover .shortcut-del { opacity: 1; }" +
                    ".shortcut-del:hover { background: #ef4444; color: #ffffff; transform: scale(1.15); }" +
                    ".shortcut-icon { width: 50px; height: 50px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: bold; margin-bottom: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.08); }" +
                    ".shortcut-title { font-size: 13px; color: #334155; text-overflow: ellipsis; overflow: hidden; white-space: nowrap; max-width: 100px; font-weight: 600; }" +
                    "</style></head><body>" +
                    "<div class='icon-wrapper'>" +
                    logoHtml +
                    "</div>" +
                    "<h1 class='title'>SGU Web Browser</h1>" +
                    "<p class='subtitle'>Trình duyệt máy chủ - Quản trị và phân phối web qua IIS</p>" +
                    shortcutsHtml.toString() +
                    "</body></html>";
        }

        private void deployAndOpenLocalFolder(String folderName) {
            File srcDir = new File("src_download", folderName);
            if (srcDir.exists() && srcDir.isDirectory()) {
                WebClient.copyAllFiles(srcDir, new File("C:\\inetpub\\site1"));
                WebClient.copyAllFiles(srcDir, new File("www"));

                String domain = folderName.startsWith("src_") ? folderName.substring(4) : folderName;
                String displayTitle = formatDomainTitle(domain);
                updateTabTitle(displayTitle);

                // Hiển thị trực tiếp URL thực tế của trang web (VD: https://www.24h.com.vn) thay vì hiện localhost:8443
                String realUrl = getOriginalUrlForDomain(domain);
                currentUrl = realUrl;
                omniboxField.setText(realUrl);
                lockBadge.setText("[HTTPS]");
                lockBadge.setForeground(new Color(24, 128, 56));

                File localIndex = new File(srcDir, "index.html");
                if (localIndex.exists()) {
                    try {
                        String html = java.nio.file.Files.readString(localIndex.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                        Platform.runLater(() -> {
                            if (fxWebEngine != null) {
                                fxWebEngine.loadContent(html, "text/html");
                            }
                        });
                        return;
                    } catch (Exception ignored) {}
                }

                // Fallback nạp qua mạng nếu không đọc được file
                navigate("https://localhost:8443");
            }
        }

        private String getOriginalUrlForDomain(String domain) {
            if (domain.contains("24h")) return "https://www.24h.com.vn";
            if (domain.contains("thongtindaotao") || domain.contains("sgu")) return "https://thongtindaotao.sgu.edu.vn";
            if (domain.contains("google")) return "https://www.google.com";
            if (domain.contains("facebook")) return "https://www.facebook.com";
            return "https://www." + domain + (domain.contains(".") ? "" : ".com");
        }

        private String formatDomainTitle(String domain) {
            if (domain.contains("24h")) return "Báo 24h";
            if (domain.contains("thongtindaotao") || domain.contains("sgu")) return "Đào tạo SGU";
            if (domain.contains("google")) return "Google";
            if (domain.contains("facebook")) return "Facebook";
            return domain.substring(0, 1).toUpperCase() + domain.substring(1);
        }

        private List<DownloadedSite> scanDownloadedSites() {
            List<DownloadedSite> list = new ArrayList<>();
            File downloadParent = new File("src_download");
            if (downloadParent.exists() && downloadParent.isDirectory()) {
                File[] folders = downloadParent.listFiles(f -> f.isDirectory() 
                        && f.getName().startsWith("src_")
                        && !f.getName().toLowerCase().contains("local")
                        && !f.getName().toLowerCase().contains("127.0.0.1"));
                if (folders != null) {
                    for (File dir : folders) {
                        File idx = new File(dir, "index.html");
                        if (idx.exists() && idx.length() > 0) {
                            String folderName = dir.getName();
                            String domain = folderName.substring("src_".length());
                            String title = formatDomainTitle(domain);
                            String badge = domain.toUpperCase();
                            String color = "#1d4ed8";
                            String bg = "#dbeafe";

                            if (domain.contains("24h")) {
                                badge = "24H";
                                color = "#b91c1c";
                                bg = "#fee2e2";
                            } else if (domain.contains("thongtindaotao") || domain.contains("sgu")) {
                                badge = "SGU";
                                color = "#1d4ed8";
                                bg = "#dbeafe";
                            } else if (domain.contains("google")) {
                                badge = "GG";
                                color = "#4338ca";
                                bg = "#e0e7ff";
                            } else if (domain.contains("facebook")) {
                                badge = "FB";
                                color = "#1877f2";
                                bg = "#e7f3ff";
                            } else {
                                badge = domain.length() > 3 ? domain.substring(0, 3).toUpperCase() : domain.toUpperCase();
                                color = "#0f766e";
                                bg = "#ccfbf1";
                            }
                            list.add(new DownloadedSite(folderName, domain, title, badge, color, bg));
                        }
                    }
                }
            }
            return list;
        }

        private static class DownloadedSite {
            final String folderName;
            final String domain;
            final String title;
            final String badge;
            final String color;
            final String bg;

            DownloadedSite(String folderName, String domain, String title, String badge, String color, String bg) {
                this.folderName = folderName;
                this.domain = domain;
                this.title = title;
                this.badge = badge;
                this.color = color;
                this.bg = bg;
            }
        }

        private void updateTabTitle(String title) {
            int tabIdx = tabbedPane.indexOfComponent(this);
            if (tabIdx != -1) {
                Component comp = tabbedPane.getTabComponentAt(tabIdx);
                if (comp instanceof JPanel) {
                    for (Component c : ((JPanel) comp).getComponents()) {
                        if (c instanceof JLabel) {
                            JLabel lbl = (JLabel) c;
                            if (lbl.getIcon() == null && (lbl.getText() == null || !lbl.getText().equals("●"))) {
                                String displayTitle = title;
                                if (displayTitle.length() > 16) {
                                    displayTitle = displayTitle.substring(0, 16) + "...";
                                }
                                lbl.setText(displayTitle);
                                break;
                            }
                        }
                    }
                }
                if (tabbedPane.getSelectedComponent() == this) {
                    ClientGUI.this.setTitle("Trang mới".equals(title) ? "SGU Web Browser" : title + " - SGU Web Browser");
                }
            }
        }

        private String extractHost(String url) {
            try {
                URI uri = new URI(url);
                String host = uri.getHost();
                int port = uri.getPort();
                return host != null ? (host + (port != -1 ? ":" + port : "")) : url;
            } catch (Exception e) {
                return "Trang web";
            }
        }

        private void goBack() {
            if (!backStack.isEmpty()) {
                forwardStack.push(currentUrl);
                String prev = backStack.pop();
                currentUrl = prev;
                omniboxField.setText(prev);
                forwardBtn.setEnabled(true);
                backBtn.setEnabled(!backStack.isEmpty());
                navigate(prev);
            }
        }

        private void goForward() {
            if (!forwardStack.isEmpty()) {
                backStack.push(currentUrl);
                String next = forwardStack.pop();
                currentUrl = next;
                omniboxField.setText(next);
                backBtn.setEnabled(true);
                forwardBtn.setEnabled(!forwardStack.isEmpty());
                navigate(next);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientGUI().setVisible(true));
    }
}
