package com.xcy.internproject.repository.mybatis;

import com.xcy.internproject.exception.DuplicateResourceException;
import com.xcy.internproject.model.User;
import com.xcy.internproject.repository.UserRepository;
import com.xcy.internproject.repository.mybatis.mapper.UserMapper;
import com.xcy.internproject.repository.mybatis.record.UserRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisUserRepository implements UserRepository {

    private final UserMapper userMapper;

    public MyBatisUserRepository(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public User save(String username, String email) {
        UserRecord record = new UserRecord();
        record.setUsername(username);
        record.setEmail(email);

        try {
            userMapper.insert(record);
        } catch (DuplicateKeyException exception) {
            throw new DuplicateResourceException("用户名或邮箱已存在");
        }

        return toUser(record);
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(userMapper.findById(id))
                .map(MyBatisUserRepository::toUser);
    }

    private static User toUser(UserRecord record) {
        return new User(record.getId(), record.getUsername(), record.getEmail());
    }
}
