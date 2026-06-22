package robots;

import robocode.*;
import robocode.util.Utils;
import java.awt.Color;
import java.awt.geom.*;

/**
 * 大香蕉 - 蛇皮走位王者
 * 特点：S形走位、自动锁敌、预测射击、躲避子弹
 */
public class DaXiangJiao extends AdvancedRobot {
    
    // 敌人信息
    private double enemyDistance = 0;
    private double enemyBearing = 0;
    private double enemyHeading = 0;
    private double enemyVelocity = 0;
    private double enemyX = 0;
    private double enemyY = 0;
    private long lastScanTime = 0;
    
    // 走位状态
    private int moveDirection = 1;  // 1或-1，控制左右移动
    private int waveCount = 0;      // 波动计数，用于S形走位
    private double firePower = 2;   // 火力
    
    // 避弹相关
    private boolean avoidBullet = false;
    private double bulletX = 0;
    private double bulletY = 0;
    
    public void run() {
        // 设置颜色 - 香蕉黄
        setBodyColor(Color.YELLOW);
        setGunColor(Color.ORANGE);
        setRadarColor(Color.YELLOW);
        setBulletColor(Color.BLACK);
        setScanColor(Color.YELLOW);
        
        // 独立控制雷达、炮塔、车身
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);
        
        // 初始扫描
        turnRadarRightRadians(Double.POSITIVE_INFINITY);
        
        while (true) {
            // 雷达持续扫描
            if (getTime() - lastScanTime > 5) {
                setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            }
            
            // 执行蛇皮走位
            snakeMove();
            
            execute();
        }
    }
    
    /**
     * S形蛇皮走位
     */
    private void snakeMove() {
        waveCount++;
        
        // 基础前进速度
        double baseVelocity = 8;
        
        // S形走位：正弦波控制转向
        double turnAngle = Math.sin(waveCount * 0.3) * 45;
        
        // 如果靠近墙壁，转向避开
        double margin = 60;
        if (getX() < margin) {
            turnAngle = -45;
        } else if (getX() > getBattleFieldWidth() - margin) {
            turnAngle = 45;
        }
        if (getY() < margin) {
            turnAngle = 180 - getHeading();
        } else if (getY() > getBattleFieldHeight() - margin) {
            turnAngle = -getHeading();
        }
        
        // 随机变速，增加命中难度
        if (Math.random() < 0.1) {
            baseVelocity = -baseVelocity;
        }
        
        setTurnRight(turnAngle);
        setAhead(100 * moveDirection);
        
        // 偶尔改变移动方向，增加不可预测性
        if (Math.random() < 0.05) {
            moveDirection *= -1;
        }
    }
    
    /**
     * 扫描到敌人
     */
    public void onScannedRobot(ScannedRobotEvent e) {
        lastScanTime = getTime();
        
        // 更新敌人信息
        enemyDistance = e.getDistance();
        enemyBearing = e.getBearingRadians();
        enemyHeading = e.getHeadingRadians();
        enemyVelocity = e.getVelocity();
        
        // 计算敌人绝对坐标
        double absoluteBearing = getHeadingRadians() + enemyBearing;
        enemyX = getX() + Math.sin(absoluteBearing) * enemyDistance;
        enemyY = getY() + Math.cos(absoluteBearing) * enemyDistance;
        
        // 雷达锁定敌人
        double radarTurn = Utils.normalRelativeAngle(absoluteBearing - getRadarHeadingRadians());
        setTurnRadarRightRadians(radarTurn * 1.5); // 1.5倍系数确保锁定
        
        // 调整火力：距离越近火力越大
        firePower = Math.min(3, Math.max(1.5, 500 / enemyDistance));
        
        // 预测射击
        predictiveAiming();
        
        // 自动开火
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10) {
            setFire(firePower);
        }
    }
    
    /**
     * 预测射击算法
     * 根据敌人速度和方向预测其未来位置
     */
    private void predictiveAiming() {
        // 子弹速度 = 20 - 3 * firePower
        double bulletSpeed = 20 - 3 * firePower;
        
        // 预测时间 = 距离 / 子弹速度
        double time = enemyDistance / bulletSpeed;
        
        // 预测敌人未来位置
        double predictedX = enemyX + Math.sin(enemyHeading) * enemyVelocity * time;
        double predictedY = enemyY + Math.cos(enemyHeading) * enemyVelocity * time;
        
        // 边界检查，防止预测出界
        predictedX = Math.max(18, Math.min(getBattleFieldWidth() - 18, predictedX));
        predictedY = Math.max(18, Math.min(getBattleFieldHeight() - 18, predictedY));
        
        // 计算预测角度
        double predictedAngle = Utils.normalAbsoluteAngle(
            Math.atan2(predictedX - getX(), predictedY - getY())
        );
        
        // 转向预测角度
        double gunTurn = Utils.normalRelativeAngle(predictedAngle - getGunHeadingRadians());
        setTurnGunRightRadians(gunTurn);
    }
    
    /**
     * 被子弹击中时
     */
    public void onHitByBullet(HitByBulletEvent e) {
        // 紧急回避：垂直于子弹方向移动
        double bulletBearing = e.getBearingRadians();
        double escapeAngle = bulletBearing + Math.PI / 2;
        
        setTurnRightRadians(Utils.normalRelativeAngle(escapeAngle - getHeadingRadians()));
        setAhead(150);
        
        // 改变移动方向
        moveDirection *= -1;
    }
    
    /**
     * 撞到墙壁时
     */
    public void onHitWall(HitWallEvent e) {
        // 后退并转向
        setBack(50);
        setTurnRight(90);
        moveDirection *= -1;
    }
    
    /**
     * 撞到其他机器人时
     */
    public void onHitRobot(HitRobotEvent e) {
        // 开火并后退
        setFire(3);
        setBack(100);
    }
    
    /**
     * 获胜时庆祝动作
     */
    public void onWin(WinEvent e) {
        // 胜利旋转
        for (int i = 0; i < 50; i++) {
            setTurnRight(30);
            setBack(5);
            execute();
        }
    }
}
