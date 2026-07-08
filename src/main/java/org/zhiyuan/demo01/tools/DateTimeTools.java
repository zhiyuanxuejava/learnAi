package org.zhiyuan.demo01.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期时间工具集。
 * 这里通过 Spring AI Tool 暴露时间相关能力，供模型按需调用。
 */
@Component
public class DateTimeTools {

    private static final Logger log = LoggerFactory.getLogger(DateTimeTools.class);

    /**
     * 获取指定时区的当前日期和时间。
     * 如果传入的时区无效，则退回到系统默认时区。
     *
     * @param zoneId 时区标识
     * @return 格式化后的当前时间
     */
    @Tool(description = "获取指定时区的当前日期和时间,用于回答需要实时时间的问题")
    public String getCurrentDateTime(
            @ToolParam(description = "时区ID，例如 Asia/Shanghai、UTC、America/New_York")
            String zoneId) {

        ZoneId targetZoneId;

        try {
            targetZoneId = ZoneId.of(zoneId);
            log.info("获取指定时区时间，zoneId={}", targetZoneId);
        }
        catch (Exception ex) {
            log.warn("无效时区，回退到系统默认时区，zoneId={}", zoneId);
            targetZoneId = ZoneId.systemDefault();
        }

        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(ZonedDateTime.now(targetZoneId));
    }

    /**
     * 设置闹钟。
     * 当前示例只演示工具调用返回值，后续如果落地业务，可以扩展为入库、推送等实际操作。
     *
     * @param alarmTime 目标提醒时间
     * @return 工具调用结果
     */
    @Tool(description = "设置闹钟，调用此工具可以指定时间触发提醒，时间参数必须是 ISO-8601 格式 ，例如:2026-05-03 13:30:00")
    public String setAlarm(@ToolParam(description = "闹钟的出发时间，标准格式：yyyy-mm-dd  HH:mm:ss") String alarmTime) {
        log.info("收到设置闹钟请求，alarmTime={}", alarmTime);
        return "闹钟已经设置，将在" + alarmTime + "提醒用户！";
    }
}
