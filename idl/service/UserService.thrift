namespace java com.lixin.thrift.service

include "UserVo.thrift"
include "UserNotFoundException.thrift"

service UserService {

    UserVo.UserVo findUserById(1: i32 userId) throws (1:UserNotFoundException.UserNotFountException e);

}