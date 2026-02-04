package com.bigbrother.bilicraftticketsystem.utils;

import com.bergerkiller.bukkit.tc.TrainCarts;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class ImageUtils {
    public static File getPlayerTicketbgFolder(Player player) {
        return new File(TrainCarts.plugin.getDataFile("images"), player.getUniqueId().toString());
    }

    public static byte[] convertTo128x128(byte[] imageBytes) throws IOException {
        // 读取图片文件
        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (originalImage == null) {
            return null;
        }

        // 获取原始图片的宽度和高度
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        // 计算缩放比例，缩放到最短边为128像素
        int targetSize = 128;
        int newWidth;
        int newHeight;

        // 按比例缩放，使长宽等比
        if (originalWidth > originalHeight) {
            newWidth = targetSize;
            newHeight = (int) (originalHeight * (targetSize / (double) originalWidth));
        } else {
            newHeight = targetSize;
            newWidth = (int) (originalWidth * (targetSize / (double) originalHeight));
        }

        // 创建一个缩放后的图片
        Image scaledImage = originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage scaledBufferedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);

        // 将缩放后的图片绘制到新的 BufferedImage 中
        Graphics2D g2d = scaledBufferedImage.createGraphics();
        g2d.drawImage(scaledImage, 0, 0, null);
        g2d.dispose();

        // 创建一个 128x128 的透明背景图片
        BufferedImage finalImage = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = finalImage.createGraphics();

        // 填充透明背景
        g.setColor(new Color(0, 0, 0, 0)); // 透明
        g.fillRect(0, 0, targetSize, targetSize);

        // 计算位置，使得缩放后的图像居中
        int xOffset = (targetSize - newWidth) / 2;
        int yOffset = (targetSize - newHeight) / 2;

        // 将缩放后的图片绘制到中心位置
        g.drawImage(scaledBufferedImage, xOffset, yOffset, null);
        g.dispose();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ImageIO.write(finalImage, "PNG", byteArrayOutputStream);

        return byteArrayOutputStream.toByteArray();
    }

    public static long getImageSize(String imageUrl) throws IOException {
        URL url = new URL(imageUrl);
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
        HttpURLConnection conn = (HttpURLConnection) new URL(imageUrl).openConnection();
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
