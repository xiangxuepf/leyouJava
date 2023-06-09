package com.leyou.order.service;

import com.alibaba.fescar.spring.annotation.GlobalTransactional;
import com.leyou.auth.pojo.UserInfo;
import com.leyou.common.enums.ExceptionEnums;
import com.leyou.common.exception.LyException;
import com.leyou.common.utils.IdWorker;
import com.leyou.item.pojo.Sku;
import com.leyou.order.client.AddressClient;
import com.leyou.order.client.GoodsClient;
import com.leyou.order.dto.AddressDTO;
import com.leyou.common.dto.CartDTO;
import com.leyou.order.dto.OrderDTO;
import com.leyou.order.enums.OrderStatusEnum;
import com.leyou.order.interceptor.OrderInterceptor;
import com.leyou.order.mapper.OrderDetailMapper;
import com.leyou.order.mapper.OrderMapper;
import com.leyou.order.mapper.OrderStatusMapper;
import com.leyou.order.pojo.Order;
import com.leyou.order.pojo.OrderDetail;
import com.leyou.order.pojo.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private OrderStatusMapper orderStatusMapper;
    @Autowired
    private IdWorker idWorker;
    @Autowired
    private GoodsClient goodsClient;

    @Transactional
//    @GlobalTransactional
    public Long createOrder(OrderDTO orderDTO) {
        // 1.新增订单;
        Order order = new Order();
        // 1.1 订单编号，基本信息;
        long orderId = idWorker.nextId();
        order.setOrderId(orderId);
        order.setCreateTime(new Date());
        order.setPaymentType(orderDTO.getPaymentType());
        // 1.2 用户信息;
        UserInfo user = OrderInterceptor.getUser();
        order.setUserId(user.getId());
        order.setBuyerNick(user.getUsername());
        order.setBuyerRate(false);
        // 1.3 收货人地址;
        // 获取收货人信息
        AddressDTO addr = AddressClient.findById(orderDTO.getAddressId());
        order.setReceiver(addr.getName());
        order.setReceiverAddress(addr.getAddress());
        order.setReceiverCity(addr.getCity());
        order.setReceiverDistrict(addr.getDistrict());
        order.setReceiverMobile(addr.getPhone());
        order.setReceiverState(addr.getState());
        order.setReceiverZip(addr.getZipCode());
        // 1.4 金额;
        // 数组转成map；
        Map<Long, Integer> numMap = orderDTO.getCarts().stream().collect(Collectors.toMap(CartDTO::getSkuId, CartDTO::getNum));
        // 获取所有skuid;
        Set<Long> ids = numMap.keySet();
        // 根据id查询sku
        List<Sku> skus = goodsClient.querySkuBySkuIds(new ArrayList<>(ids));
        // 准备orderDetail集合
        List<OrderDetail> details = new ArrayList<>();
        // 计算金额
        long totalPay = 0L;
        for(Sku sku : skus){
            // 计算商品总价
            totalPay += sku.getPrice() * numMap.get(sku.getId());
            // 封装orderDetail
            OrderDetail detail = new OrderDetail();
            detail.setImage(StringUtils.substringBefore(sku.getImages(), ","));
            detail.setNum(numMap.get(sku.getId()));
            detail.setOrderId(orderId);
            detail.setOwnSpec(sku.getOwnSpec());
            detail.setPrice(sku.getPrice());
            detail.setSkuId(sku.getId());
            detail.setTitle(sku.getTitle());
            details.add(detail);
        }
        // 设置总金额;
        order.setTotalPay(totalPay);
        // 设置实付金额 总金额 + 邮费 - 优惠金额  邮费暂时给0
        order.setActualPay(totalPay + 0 - 0);
        // 1.5 order写入数据库
        int count = orderMapper.insertSelective(order);
        if(count != 1){
            log.error("[创建订单] 创建订单失败, orderId:{}", orderId);
            throw new LyException(ExceptionEnums.CREATE_ORDER_ERROR);
        }
        // 2.新增订单详情;
        count = orderDetailMapper.insertList(details);
        if(count != details.size()){
            log.error("[创建订单] 创建订单失败, orderId:{}", orderId);
            throw new LyException(ExceptionEnums.CREATE_ORDER_ERROR);
        }
        // 3.新增订单状态;
        OrderStatus orderStatus = new OrderStatus();
        orderStatus.setCreateTime(order.getCreateTime());
        orderStatus.setOrderId(orderId);
        orderStatus.setStatus((OrderStatusEnum.UN_PAY.value()));
        orderStatusMapper.insertSelective(orderStatus);
        // 4.减库存;
        List<CartDTO> cartDTOS = orderDTO.getCarts();
        goodsClient.decreaseStock(cartDTOS);
//        int i = 1/0;
        return orderId;
    }

    public Order queryOrderById(Long id) {
       // 查询订单
       Order order = orderMapper.selectByPrimaryKey((id));
       if(order == null){
           throw new LyException(ExceptionEnums.ORDER_NOT_FOUND);
       }
       // 查询订单详情
        OrderDetail detail = new OrderDetail();
       detail.setOrderId(id);
       List<OrderDetail> details = orderDetailMapper.select(detail);
       if(CollectionUtils.isEmpty(details)){
           throw new LyException(ExceptionEnums.ORDER_DETAIL_NOT_FOUND);
       }
       order.setOrderDetails(details);
       // 查询订单状态
        OrderStatus orderStatus = orderStatusMapper.selectByPrimaryKey(id);
        if(orderStatus == null){
            throw new LyException(ExceptionEnums.ORDER_STATUS_NOT_FOUND);
        }
        order.setOrderStatus(orderStatus);
        return  order;
    }
}
