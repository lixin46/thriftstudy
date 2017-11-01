package com.lixin.thrift.service.impl;

import com.lixin.thrift.exception.UserNotFountException;
import com.lixin.thrift.service.UserService;
import com.lixin.thrift.vo.UserVo;
import org.apache.thrift.TException;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by lixin46 on 2017/10/27.
 */
public class UserServiceImpl implements UserService.Iface {

    private Map<Integer,UserVo> data = new HashMap<>();

    public UserServiceImpl() {
        data.put(1,new UserVo("zhangsan",20));
        data.put(2,new UserVo("lisi",25));

    }

    @Override
    public UserVo findUserById(int userId) throws UserNotFountException, TException {
        return data.get(userId);
    }
}
