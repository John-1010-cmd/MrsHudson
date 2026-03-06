package com.mrshudson.service;

/**
 * 推送服务接口
 */
public interface PushService {

    /**
     * 注册设备Token
     *
     * @param userId       用户ID
     * @param deviceToken  设备FCM Token
     * @param platform     平台类型 (android/ios)
     * @return 是否注册成功
     */
    boolean registerDevice(Long userId, String deviceToken, String platform);

    /**
     * 发送通知给用户的所有设备
     *
     * @param userId  用户ID
     * @param title  通知标题
     * @param body   通知内容
     * @return 发送成功的设备数量
     */
    int sendNotificationToUser(Long userId, String title, String body);

    /**
     * 发送通知给指定设备
     *
     * @param deviceToken 设备Token
     * @param title      通知标题
     * @param body       通知内容
     * @return 是否发送成功
     */
    boolean sendNotificationToDevice(String deviceToken, String title, String body);

    /**
     * 移除用户的设备Token
     *
     * @param userId      用户ID
     * @param deviceToken 设备Token
     * @return 是否移除成功
     */
    boolean removeDeviceToken(Long userId, String deviceToken);
}
