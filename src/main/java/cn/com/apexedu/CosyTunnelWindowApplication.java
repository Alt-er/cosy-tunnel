package cn.com.apexedu;

import cn.com.apexedu.client.tcp.ConnectionManager;
import cn.com.apexedu.client.websocket.WebSocketClient;
import io.netty.util.internal.PlatformDependent;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.io.IoBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CosyTunnelWindowApplication extends JFrame {

    final private static Logger logger = LoggerFactory.getLogger(CosyTunnelWindowApplication.class);

    private JTextPane configTextPane;
    private JTextArea logTextArea;
    private JButton saveButton;
    JTabbedPane tabbedPane;
    private Timer timer;
    private File logFile;
    private long lastModified;

    public CosyTunnelWindowApplication() throws BadLocationException {

        // 设置窗口标题
        super("Cosy tunnel");
        // 设置窗口大小
        setSize(500, 300);
        // 设置窗口关闭时退出程序
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // 创建选项卡面板
        tabbedPane = new JTabbedPane();
        String configContent = "";
        try {
            File configFile = new File("./tun.conf");
            if (configFile.exists()) {
                // 读取配置文件内容
                configContent = Files.readString(configFile.toPath());
                // 在输入框中展示配置文件内容
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


        // 创建"配置"选项卡
        configTextPane = new JTextPane();
        StyledDocument doc = configTextPane.getStyledDocument();
        // 设置样式
        Style grayStyle = doc.addStyle("Gray", null);
        StyleConstants.setForeground(grayStyle, Color.GRAY);
        Style normalStyle = doc.addStyle("Normal", null);
        StyleConstants.setForeground(normalStyle, Color.BLACK);
        configTextPane.getDocument().addDocumentListener(new DocumentListener() {
            private void updateStyle() {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        StyledDocument doc = (StyledDocument) configTextPane.getDocument(); // 获取StyledDocument对象
                        Element root = doc.getDefaultRootElement();
                        // 设置样式的前景色为灰色
//                        System.out.println("insert");

                        for (int i = 0; i < root.getElementCount(); i++) {
                            Element elem = root.getElement(i);
                            if (elem.getStartOffset() == elem.getEndOffset()) {
                                // 忽略空行
                                continue;
                            }
                            try {
                                String text = doc.getText(elem.getStartOffset(), elem.getEndOffset() - elem.getStartOffset());
                                if (text.trim().startsWith("#")) {
                                    doc.setCharacterAttributes(elem.getStartOffset(), elem.getEndOffset() - elem.getStartOffset(),
                                            grayStyle, false);
                                } else {
                                    doc.setCharacterAttributes(elem.getStartOffset(), elem.getEndOffset() - elem.getStartOffset(),
                                            normalStyle, false);
                                }
                            } catch (BadLocationException e1) {
                                e1.printStackTrace();
                            }
                        }
                    }

                });
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                updateStyle();

            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateStyle();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });

        configTextPane.setContentType("text/plain"); // 设置文本类型为普通文本
        configTextPane.setText(configContent); // 设置文本内容
        configTextPane.setEditable(true); // 设置文本框可编辑


        JScrollPane configScrollPane = new JScrollPane(configTextPane);
        JPanel configPanel = new JPanel();
        configPanel.setLayout(new BorderLayout());
        configPanel.add(configScrollPane, BorderLayout.CENTER);
        JPanel configButtonPanel = new JPanel();
        saveButton = new JButton("保存配置&启动代理");
        saveButton.addActionListener(new SaveButtonListener());
        configButtonPanel.add(saveButton);
        configPanel.add(configButtonPanel, BorderLayout.SOUTH);
        tabbedPane.addTab("配置", configPanel);

        //
        // 创建表格模型
        DefaultTableModel model = new DefaultTableModel();
        model.addColumn("路由IP");
        model.addColumn("目标服务");

        // 创建表格并将其添加到滚动面板中
        JTable table = new JTable(model) {
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return false;
            }
        };
//        table.setEnabled(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); // 设置表格选择模式为单选模式
        JScrollPane scrollPane = new JScrollPane(table);
        tabbedPane.addTab("路由", scrollPane);
        ConnectionManager.listenRouteRelatedChange((k, v) -> {
            model.addRow(new String[]{k, v.getWebsocketURI().toString()});
        });

        // 创建"日志"选项卡
        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setLineWrap(true);
        JScrollPane logScrollPane = new JScrollPane(logTextArea);
        JPanel logPanel = new JPanel();
        logPanel.setLayout(new BorderLayout());
        logPanel.add(logScrollPane, BorderLayout.CENTER);
        tabbedPane.addTab("日志", logPanel);
        // 添加选项卡面板到窗口中
        Container c = getContentPane();
        c.setLayout(new BorderLayout());
        c.add(tabbedPane, BorderLayout.CENTER);

        // 创建一个标签
        JLabel label = new JLabel("实时速率: 0 KB/s");

        label.setForeground(new Color(64, 64, 48));
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        // 创建一个空边框并将其应用于标签
        EmptyBorder margin = new EmptyBorder(0, 0, 5, 10); // 上,左,下,右
        label.setBorder(BorderFactory.createCompoundBorder(
                label.getBorder(),
                margin));
        c.add(label, BorderLayout.PAGE_END);

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            private long lastDownloadBytes = 0;
            private long lastUploadBytes = 0;

            @Override
            public void run() {
                long totalBytes = ConnectionManager.totalBytes.get();
                long downloadBytes = ConnectionManager.downloadBytes.get();
                long uploadBytes = ConnectionManager.uploadBytes.get();
                long diff2 = downloadBytes - lastDownloadBytes;
                long diff3 = uploadBytes - lastUploadBytes;

                lastDownloadBytes = downloadBytes;
                lastUploadBytes = uploadBytes;
//                System.out.println(totalBytes +"  "+downloadBytes+ "  "+uploadBytes );
                label.setText("[总流量: " + totalBytes / 1000 / 1000d + " MB]    [下载: " + (diff2 / 1000d) + " KB/s]    [上传: " + (diff3 / 1000d) + " KB/s]");


            }
        }, 0, 1000); // 每秒钟更新一次标签


        // 显示窗口
        setVisible(true);

        // 实时更新日志内容
        logFile = new File("./logs/cosy.log");
        lastModified = logFile.lastModified();
        timer.schedule(new LogTimerTask(), 0, 1000);
    }

    private class SaveButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            // 保存配置
            String config = configTextPane.getText();

            try {
                Files.writeString(Path.of("./tun.conf"), config);
                saveButton.setEnabled(false);
                tabbedPane.setSelectedIndex(1);
                CosyTunnelApplication.main(new String[]{});
//                JOptionPane.showMessageDialog(CosyTunnelWindowApplication.this, "配置已保存", "保存成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                logger.error("error:", ex);
                JOptionPane.showMessageDialog(CosyTunnelWindowApplication.this, "配置保存失败: " + ex.getMessage(), "保存失败", JOptionPane.ERROR_MESSAGE);
            } catch (URISyntaxException ex) {
                logger.error("error:", ex);
                JOptionPane.showMessageDialog(CosyTunnelWindowApplication.this, "配置保存失败: URISyntaxException=> " + ex.getMessage(), "保存失败", JOptionPane.ERROR_MESSAGE);
            } catch (Throwable ex) {
                logger.error("error:", ex);
                JOptionPane.showMessageDialog(CosyTunnelWindowApplication.this, "配置保存失败: " + ex.getMessage(), "保存失败", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private class LogTimerTask extends TimerTask {

        public String readLastLines(File filePath, int num) {
            StringBuilder result = new StringBuilder();
            try (RandomAccessFile randomAccessFile = new RandomAccessFile(filePath, "r")) {
                long fileLength = randomAccessFile.length();
                long position = fileLength - 1;
                int count = 0;
                while (position > 0 && count < num) {
                    randomAccessFile.seek(position);
                    byte currentByte = randomAccessFile.readByte();
                    if (currentByte == '\n') {
                        count++;
                    }
                    position--;
                }
                if (position == 0) {
                    randomAccessFile.seek(0);
                }
                byte[] bytes = new byte[(int) (fileLength - position)];
                randomAccessFile.read(bytes);
                result.append(new String(bytes, PlatformDependent.isWindows() ? "GBK" : "UTF-8"));
            } catch (Exception e) {
                // handle exception
                e.printStackTrace();
            }
            return result.toString();
        }

        @Override
        public void run() {
            if (logFile.lastModified() > lastModified) {
                // 读取新的日志内容
                String strings = readLastLines(logFile, 50);
                logTextArea.setText(strings);
                lastModified = logFile.lastModified();
            }
        }
    }


    public static void main(String[] args) throws BadLocationException, IOException {
        redirectSystemOutAndErrToLog();
        // 创建窗口对象并显示
        CosyTunnelWindowApplication window = new CosyTunnelWindowApplication();
        window.setVisible(true);
    }

    private static void redirectSystemOutAndErrToLog() {

        PrintStream debugPrintStream = IoBuilder.forLogger(CosyTunnelWindowApplication.class)
                .setLevel(Level.DEBUG)
                .buildPrintStream();
        PrintStream errorPrintStream = IoBuilder.forLogger(CosyTunnelWindowApplication.class)
                .setLevel(Level.ERROR)
                .buildPrintStream();
        System.setOut(debugPrintStream);
        System.setErr(errorPrintStream);
        System.out.println("Successfully redirect system out and err to log");
    }
}

