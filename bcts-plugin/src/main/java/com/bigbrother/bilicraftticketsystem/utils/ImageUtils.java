package com.bigbrother.bilicraftticketsystem.utils;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class ImageUtils {
    public static File getImageFolder() {
        return new File(BiliCraftTicketSystem.plugin.getDataFolder(), "images");
    }

    public static File getLogoFolder() {
        return new File(getImageFolder(), "logo");
    }

    public static File getPlayerTicketbgFolder(Player player) {
        return new File(getImageFolder(), player.getUniqueId().toString());
    }

    /**
     * 获取铁路系统WebUI界面logo图片的显示路径
     *
     * @param systemId 铁路系统id
     * @return 图片路径
     */
    public static File getSystemImageFileMc(String systemId) {
        return new File(getLogoFolder(), "web" + File.separator + systemId + ".png");
    }

    /**
     * 获取铁路系统WebUI界面logo图片的显示路径
     *
     * @param systemId 铁路系统id
     * @return 图片路径
     */
    public static File getSystemImageFileWeb(String systemId) {
        return new File(getLogoFolder(), "mc" + File.separator + systemId + ".png");
    }

    /**
     * 将图片转化为n x n的图片，其余位置用透明像素填充
     *
     * @param imageBytes 图片
     * @param n          转化后的图片边长
     * @return 转化后的图片
     * @throws IOException 不是合法的图片
     */
    public static byte[] convertTonxn(byte[] imageBytes, int n) throws IOException {
        // 读取图片文件
        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (originalImage == null) {
            return null;
        }

        // 获取原始图片的宽度和高度
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        // 计算缩放比例，缩放到最短边为128像素
        int newWidth;
        int newHeight;

        // 按比例缩放，使长宽等比
        if (originalWidth > originalHeight) {
            newWidth = n;
            newHeight = (int) (originalHeight * (n / (double) originalWidth));
        } else {
            newHeight = n;
            newWidth = (int) (originalWidth * (n / (double) originalHeight));
        }

        // 创建一个缩放后的图片
        Image scaledImage = originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage scaledBufferedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);

        // 将缩放后的图片绘制到新的 BufferedImage 中
        Graphics2D g2d = scaledBufferedImage.createGraphics();
        g2d.drawImage(scaledImage, 0, 0, null);
        g2d.dispose();

        // 创建一个 128x128 的透明背景图片
        BufferedImage finalImage = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = finalImage.createGraphics();

        // 填充透明背景
        g.setColor(new Color(0, 0, 0, 0)); // 透明
        g.fillRect(0, 0, n, n);

        // 计算位置，使得缩放后的图像居中
        int xOffset = (n - newWidth) / 2;
        int yOffset = (n - newHeight) / 2;

        // 将缩放后的图片绘制到中心位置
        g.drawImage(scaledBufferedImage, xOffset, yOffset, null);
        g.dispose();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ImageIO.write(finalImage, "PNG", byteArrayOutputStream);

        return byteArrayOutputStream.toByteArray();
    }

    public static long getImageSize(String imageUrl) throws IOException {
        URL url = URI.create(imageUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("HEAD");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            return -1;
        }

        return conn.getContentLengthLong();
    }

    public static byte[] getImageBytes(String imageUrl) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(imageUrl).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (InputStream inputStream = conn.getInputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        return outputStream.toByteArray();
    }
}
