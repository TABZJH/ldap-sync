package com.willowleaf.ldapsync.domain.porter;

import com.willowleaf.ldapsync.domain.*;
import lombok.SneakyThrows;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.FutureTask;

import static com.willowleaf.ldapsync.domain.Dictionary.Type.*;

/**
 * <pre>
 * 循环调用接口拉取LDAP数据。
 *
 * 根据部门信息拉取部门下的所有员工信息。
 * </pre>
 */
public class CycleLdapPorter extends LdapPorter {

    public CycleLdapPorter(@Nonnull DataSource dataSource, @Nonnull Organization.Storage storage) {
        super(dataSource, storage);
    }

    @SneakyThrows
    public Organization pull() {
        // async 1. 异步获取所有的岗位信息
        FutureTask<List<Position>> positionTask = new FutureTask<>(() ->
                pullElements(dataSource.getDictionary(POSITION), Position.class));
        new Thread(positionTask).start();

        // 1. 获取部门信息
        List<Department> departments = pullElements(dataSource.getDictionary(DEPARTMENT), Department.class);

        // 2. 获部门的员工信息
        Dictionary employeeDictionary = dataSource.getDictionary(EMPLOYEE);
        String sourceName = employeeDictionary.getAttributeMaps()
                .stream()
                .filter(attributeMap -> "departmentNumber".equals(attributeMap.getTargetName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("员工信息（departmentNumber）没有配置LDAP映射信息."))
                .getSourceName();
        departments.parallelStream()
                .forEach(department -> {
                    List<Employee> employees = pullEmployeeElements(employeeDictionary,
                            sourceName, department.getNumber());
                    department.setEmployees(employees);
                    employees.forEach(employee -> employee.setDepartment(department));
                });

        // 3. 获取员工的岗位信息
        List<Position> positions = positionTask.get();
        return new Organization(dataSource, departments, positions, storage);
    }

}
