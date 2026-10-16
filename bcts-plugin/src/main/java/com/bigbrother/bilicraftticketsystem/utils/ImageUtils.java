package com.bigbrother.bilicraftticketsystem.utils;

import com.bigbrother.bilicraftticketsystem.BiliCraftTicketSystem;
import com.bigbrother.bilicraftticketsystem.config.MapConfig;
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
        return new File(getLogoFolder(), "mc" + File.separator + systemId + ".png");
    }

    /**
     * 获取铁路系统WebUI界面logo图片的显示路径
     *
     * @param systemId 铁路系统id
     * @return 图片路径
     */
    public static File getSystemImageFileWeb(String systemId) {
        return new File(getLogoFolder(), "web" + File.separator + systemId + ".png");
    }

    /**
     * 铁路系统 logo 原图大小上限（与向导一致：3MB）。
     */
    public static final long MAX_LOGO_BYTES = 3000 * 1024;

    /**
     * 把原图缩放并保存为铁路系统 logo（向导下载与网页端上传共用）。
     * <ul>
     *   <li>{@code isWeb=true}：保存网页 logo（web-logo-dim）；若游戏内 logo 尚不存在，同时用该图生成游戏内 logo。</li>
     *   <li>{@code isWeb=false}：保存游戏内 logo（mc-logo-dim）。</li>
     * </ul>
     * 涉及文件 IO 与图片处理，应在异步线程调用。
     *
     * @param systemId   铁路系统 id
     * @param imageBytes 原图字节
     * @param isWeb      是否网页 logo
     * @return 失败时返回中文错误提示；成功返回 null
     * @throws IOException 写文件失败
     */
    public static String saveSystemLogo(String systemId, byte[] imageBytes, boolean isWeb) throws IOException {
        if (imageBytes == null || imageBytes.length == 0) {
            return "图片为空";
        }
        if (imageBytes.length > MAX_LOGO_BYTES) {
            return "图片大小不能超过3MB，当前大小：" + (imageBytes.length / 1024 / 1024) + " MB";
        }
        if (isWeb) {
            String err = writeResized(imageBytes, MapConfig.getWebLogoDim(), getSystemImageFileWeb(systemId));
            if (err != null) {
                return err;
            }
        }
        if (!isWeb || !getSystemImageFileMc(systemId).exists()) {
            return writeResized(imageBytes, MapConfig.getMcLogoDim(), getSystemImageFileMc(systemId));
        }
        return null;
    }

    private static String writeResized(byte[] imageBytes, int dim, File target) throws IOException {
        byte[] resized;
        try {
            resized = convertTonxn(imageBytes, dim);
        } catch (IOException e) {
            return "图片格式不支持或图片损坏！错误信息：" + e.getMessage();
        }
        if (resized == null) {
            return "图片尺寸转化失败";
        }
        if (!target.getParentFile().exists()) {
            //noinspection ResultOfMethodCallIgnored
            target.getParentFile().mkdirs();
        }
        java.nio.file.Files.write(target.toPath(), resized);
        return null;
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
