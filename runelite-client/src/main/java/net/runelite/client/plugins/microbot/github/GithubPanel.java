package net.runelite.client.plugins.microbot.github;

import lombok.SneakyThrows;
import net.runelite.client.RuneLite;
import net.runelite.client.plugins.microbot.github.models.FileInfo;
import net.runelite.client.plugins.microbot.sideloading.MicrobotPluginManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class GithubPanel extends PluginPanel {

    private JTextField repoField = new JTextField("https://github.com/chsami/microbot", 10);
    private JTextField folderField = new JTextField("", 10);
    private JTextField tokenField = new JTextField("", 10);

    private DefaultListModel<FileInfo> listModel = new DefaultListModel<FileInfo>();
    private JList<FileInfo> fileList = new JList<>(listModel);
    private JButton fetchButton = new JButton("Fetch from GitHub");
    private JButton downloadButton = new JButton("Download Selected");
    private JButton downloadAllButton = new JButton("Download All");
    private JButton openMicrobotSideLoadPluginFolder = new JButton("Open side-loading folder");

    @Inject
    MicrobotPluginManager microbotPluginManager;

    @Inject
    public GithubPanel() {


    //    setBorder(createCenteredTitledBorder("Download Plugins From Github Repository", "/net/runelite/client/plugins/microbot/shortestpath/Farming_patch_icon.png"));

        // Top panel for inputs
        // Keep BoxLayout
        JPanel inputPanel = new JPanel(new GridBagLayout());
        /*inputPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        inputPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE),
                "Github Repository",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 12),
                Color.WHITE
        ));*/
        GridBagConstraints gbc = new GridBagConstraints();


        gbc.insets = new Insets(2, 2, 2, 2); // Add some padding
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.anchor = GridBagConstraints.WEST;

        GridBagConstraints gbci = new GridBagConstraints();


        gbci.insets = new Insets(10, 2, 10, 2); // Add some padding
        gbci.fill = GridBagConstraints.HORIZONTAL;
        gbci.weightx = 1.0;
        gbci.gridwidth = GridBagConstraints.REMAINDER;
        gbci.anchor = GridBagConstraints.WEST;

        inputPanel.add(new JLabel("Repo Url:*"), gbc);
        repoField.setBorder(BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE));
        inputPanel.add(repoField, gbci);

        inputPanel.add(new JLabel("Folder: (empty = root folder)"), gbc);
        folderField.setBorder(BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE));

        inputPanel.add(folderField, gbci);

        inputPanel.add(new JLabel("Token:"), gbc);
        tokenField.setBorder(BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE));
        inputPanel.add(tokenField, gbci);
        inputPanel.add(new JLabel(""), gbci);


        // Button panel
        JPanel buttonPanel = new JPanel(new GridLayout(5, 1, 10, 10));
        fetchButton.setBorder(BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE));
        downloadButton.setBorder(BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE));
        downloadAllButton.setBorder(BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE));
        openMicrobotSideLoadPluginFolder.setBorder(BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE));
        buttonPanel.add(fetchButton);
        buttonPanel.add(downloadButton);
        buttonPanel.add(downloadAllButton);
        buttonPanel.add(openMicrobotSideLoadPluginFolder);
        buttonPanel.add(new JLabel(""));

        // Main layout
        setLayout(new BorderLayout());
        add(inputPanel, BorderLayout.NORTH);
        add(buttonPanel, BorderLayout.CENTER);
        add(new JScrollPane(fileList), BorderLayout.SOUTH);


        fileList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof FileInfo) {
                    FileInfo fileInfo = (FileInfo) value;
                    File localFile = new File(RuneLite.RUNELITE_DIR, "microbot-plugins/" + fileInfo.getName());
                    boolean exists = localFile.exists();

                    if (exists) {
                        label.setText("✔ " + fileInfo.getName());
                        label.setForeground(Color.GREEN.darker());
                    } else {
                        label.setText(fileInfo.getName());
                        label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
                    }
                }
                return label;
            }
        });

        // Button actions
        fetchButton.addActionListener(e -> fetchFiles());
        downloadButton.addActionListener(e -> downloadSelected());
        downloadAllButton.addActionListener(e -> downloadAll());
        openMicrobotSideLoadPluginFolder.addActionListener(e -> openMicrobotSideLoadingFolder());


        // Empty cell to align button

        // Button Action

    }

    /**
     * Deletes all files in the downloads directory.
     */
    private void openMicrobotSideLoadingFolder() {
        String userHome = System.getProperty("user.home");
        File folder = new File(userHome, ".runelite/microbot-plugins");

        if (folder.exists()) {
            try {
                Desktop.getDesktop().open(folder);
            } catch (IOException e) {
                System.err.println("Failed to open folder: " + e.getMessage());
            }
        } else {
            System.err.println("Folder does not exist: " + folder.getAbsolutePath());
        }
    }

    /**
     * Downloads all files in the specified GitHub repository folder.
     */
    @SneakyThrows
    private void downloadAll() {
        if (folderField.getText().isEmpty() && GithubDownloader.isLargeRepo(repoField.getText(), tokenField.getText())) {
            int choice = JOptionPane.showConfirmDialog(this,
                    String.format("⚠ The repository is over 50MB.\nAre you sure you want to continue?"),
                    "Large Repository",
                    JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }
        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = createLoadingDialog(parentWindow, "Scanning Repo...");

        List<FileInfo> allFiles = GithubDownloader.getAllFilesRecursively(repoField.getText(), folderField.getText(), tokenField.getText());

        dialog.setVisible(false);
        parentWindow.remove(dialog);
        // Create progress dialog
        JDialog progressDialog = new JDialog(parentWindow, "loader message...", Dialog.ModalityType.APPLICATION_MODAL);
        JProgressBar progressBar = new JProgressBar(0, allFiles.size());
        progressBar.setStringPainted(true);
        progressDialog.add(progressBar);
        progressDialog.setSize(300, 75);
        progressDialog.setLocationRelativeTo(this);

        // Background task
        SwingWorker<Void, Integer> worker = new SwingWorker<>() {
            @SneakyThrows
            @Override
            protected Void doInBackground() {
                for (int i = 0; i < allFiles.size(); i++) {
                    FileInfo fileInfo = allFiles.get(i);
                    String downloadUrl = fileInfo.getUrl();
                    String fileName = fileInfo.getName();
                    System.out.println("Downloading file: " + fileName);
                    GithubDownloader.downloadFile(downloadUrl);
                    publish(i + 1);
                }
                return null;
            }

            @Override
            protected void process(List<Integer> chunks) {
                int latest = chunks.get(chunks.size() - 1);
                progressBar.setValue(latest);
            }

            @SneakyThrows
            @Override
            protected void done() {
                progressDialog.dispose();
                fileList.repaint(); // update any downloaded indicators
                JOptionPane.showConfirmDialog(parentWindow, "All files downloaded.", "Download Succesfull!",
                        JOptionPane.DEFAULT_OPTION);
            }
        };

        worker.execute();
        progressDialog.setVisible(true); // blocks until worker finishes
        microbotPluginManager.loadSideLoadPlugins();

    }

    /**
     * Downloads the selected files in the list.
     */
    @SneakyThrows
    private void downloadSelected() {
        List<FileInfo> selectedFileInfoList = fileList.getSelectedValuesList();
        for (FileInfo fileInfo : selectedFileInfoList) {
            GithubDownloader.downloadFile(fileInfo.getUrl());
        }
        fileList.repaint();
    }

    /**
     * Fetches the files in the specified GitHub repository folder and adds them to the list.
     */
    private void fetchFiles() {
        String json = GithubDownloader.fetchFiles(repoField.getText(), folderField.getText(), tokenField.getText());
        JSONArray arr = new JSONArray(json);

        listModel.clear();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            if (obj.getString("type").equals("file")) {
                String fileName = obj.getString("name");
                String downloadUrl = obj.getString("download_url");
                listModel.addElement(new FileInfo(fileName, downloadUrl));
            }
        }
    }

    private JDialog createLoadingDialog(Window parent, String message) {
        JDialog dialog = new JDialog(parent, "Please wait...", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setSize(300, 100);
        dialog.setLayout(new BorderLayout());

        JLabel label = new JLabel(message, SwingConstants.CENTER);
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);

        dialog.add(label, BorderLayout.NORTH);
        dialog.add(progressBar, BorderLayout.CENTER);
        dialog.setLocationRelativeTo(parent);
        return dialog;
    }
}
