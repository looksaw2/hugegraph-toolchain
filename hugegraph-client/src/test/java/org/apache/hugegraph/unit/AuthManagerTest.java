/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.apache.hugegraph.unit;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.hugegraph.client.RestClient;
import org.apache.hugegraph.driver.AuthManager;
import org.apache.hugegraph.rest.RestHeaders;
import org.apache.hugegraph.rest.RestResult;
import org.apache.hugegraph.structure.auth.Access;
import org.apache.hugegraph.structure.auth.Belong;
import org.apache.hugegraph.structure.auth.Group;
import org.apache.hugegraph.structure.auth.HugePermission;
import org.apache.hugegraph.structure.auth.Login;
import org.apache.hugegraph.structure.auth.Project;
import org.apache.hugegraph.structure.auth.Target;
import org.apache.hugegraph.structure.auth.TokenPayload;
import org.apache.hugegraph.structure.auth.User;
import org.apache.hugegraph.structure.auth.UserManager;
import org.apache.hugegraph.testutil.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class AuthManagerTest extends BaseUnitTest {

    private RestClient client;
    private AuthManager auth;

    @Before
    public void setup() {
        this.client = Mockito.mock(RestClient.class);
        this.auth = new AuthManager(this.client, "DEFAULT", "hugegraph");
    }

    @Test
    public void testTargetCrudAndListUseGraphSpacePath() {
        Mockito.when(this.client.post(Mockito.anyString(), Mockito.any()))
               .thenReturn(result("{\"id\":\"t1\",\"target_name\":\"graphTarget\","
                                  + "\"graphspace\":\"DEFAULT\","
                                  + "\"target_graph\":\"hugegraph\"}"));
        Mockito.when(this.client.get(Mockito.anyString(), Mockito.anyString()))
               .thenReturn(result("{\"id\":\"t1\",\"target_name\":\"graphTarget\"}"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor =
                ArgumentCaptor.forClass(Map.class);
        Mockito.when(this.client.get(Mockito.anyString(), paramsCaptor.capture()))
               .thenReturn(result("{\"targets\":[{\"id\":\"t1\","
                                  + "\"target_name\":\"graphTarget\"}]}"));
        Mockito.when(this.client.put(Mockito.anyString(), Mockito.anyString(),
                                     Mockito.any()))
               .thenReturn(result("{\"id\":\"t1\",\"target_name\":\"renamed\"}"));
        Target target = new Target();
        target.setId("t1");
        target.name("graphTarget");

        Target created = this.auth.createTarget(target);
        Target loaded = this.auth.getTarget("t1");
        List<Target> targets = this.auth.listTargets(3);
        target.name("renamed");
        Target updated = this.auth.updateTarget(target);
        this.auth.deleteTarget("t1");

        Assert.assertEquals("graphTarget", created.name());
        Assert.assertEquals("graphTarget", loaded.name());
        Assert.assertEquals(1, targets.size());
        Assert.assertEquals("renamed", updated.name());
        Assert.assertEquals(3, paramsCaptor.getValue().get("limit"));
        Mockito.verify(this.client).post("graphspaces/DEFAULT/auth/targets", target);
        Mockito.verify(this.client).get("graphspaces/DEFAULT/auth/targets", "t1");
        Mockito.verify(this.client).put("graphspaces/DEFAULT/auth/targets", "t1", target);
        Mockito.verify(this.client).delete("graphspaces/DEFAULT/auth/targets", "t1");
    }

    @Test
    public void testUserBatchRoleAndLimitValidation() {
        User user = new User();
        user.setId("u1");
        user.name("marko");
        Mockito.when(this.client.post(Mockito.endsWith("/auth/users"), Mockito.same(user)))
               .thenReturn(result("{\"id\":\"u1\",\"user_name\":\"marko\"}"));
        Mockito.when(this.client.post(Mockito.endsWith("/auth/users/batch"),
                                      Mockito.any()))
               .thenReturn(result("{\"created\":[{\"user_name\":\"marko\"}]}"));
        Mockito.when(this.client.get(Mockito.endsWith("/auth/users/u1/role")))
               .thenReturn(result("{\"roles\":{}}"));

        User created = this.auth.createUser(user);
        Map<String, String> row = new HashMap<>();
        row.put("user_name", "marko");
        Map<String, List<Map<String, String>>> batch = this.auth.createBatch(
                Collections.singletonList(row));
        User.UserRole role = this.auth.getUserRole("u1");

        Assert.assertEquals("marko", created.name());
        Assert.assertEquals(1, batch.get("created").size());
        Assert.assertTrue(role.roles().isEmpty());
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            this.auth.listUsers(0);
        });
    }

    @Test
    public void testAccessAndBelongListFiltersFormatEntityIds() {
        Group group = new Group();
        group.setId("g1");
        Target target = new Target();
        target.setId("t1");
        User user = new User();
        user.setId("u1");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> accessParams =
                ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> belongParams =
                ArgumentCaptor.forClass(Map.class);
        Mockito.when(this.client.get(Mockito.endsWith("/auth/accesses"),
                                     accessParams.capture()))
               .thenReturn(result("{\"accesses\":[{\"id\":\"a1\","
                                  + "\"group\":\"g1\","
                                  + "\"target\":\"t1\","
                                  + "\"access_permission\":\"READ\"}]}"));
        Mockito.when(this.client.get(Mockito.endsWith("/auth/belongs"),
                                     belongParams.capture()))
               .thenReturn(result("{\"belongs\":[{\"id\":\"b1\","
                                  + "\"user\":\"u1\","
                                  + "\"group\":\"g1\"}]}"));

        List<Access> accesses = this.auth.listAccessesByGroup(group, 5);
        List<Belong> belongs = this.auth.listBelongsByUser(user, 6);

        Assert.assertEquals(1, accesses.size());
        Assert.assertEquals("g1", accessParams.getValue().get("group"));
        Assert.assertEquals(null, accessParams.getValue().get("target"));
        Assert.assertEquals(5, accessParams.getValue().get("limit"));
        Assert.assertEquals(1, belongs.size());
        Assert.assertEquals("u1", belongParams.getValue().get("user"));
        Assert.assertEquals(null, belongParams.getValue().get("group"));
        Assert.assertEquals(6, belongParams.getValue().get("limit"));
    }

    @Test
    public void testProjectGraphActionsUseActionParametersAndBodyGraphs() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor =
                ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Project> bodyCaptor = ArgumentCaptor.forClass(Project.class);
        Mockito.when(this.client.put(Mockito.endsWith("/auth/projects"),
                                     Mockito.eq("p1"), bodyCaptor.capture(),
                                     paramsCaptor.capture()))
               .thenReturn(result("{\"id\":\"p1\","
                                  + "\"project_name\":\"project\","
                                  + "\"project_graphs\":[\"g1\"]}"));

        Set<String> addGraphs = new HashSet<>();
        addGraphs.add("g1");
        Set<String> removeGraphs = new HashSet<>();
        removeGraphs.add("g2");
        Project project = this.auth.projectAddGraphs("p1", addGraphs);
        Project removed = this.auth.projectRemoveGraphs("p1", removeGraphs);

        Assert.assertEquals("p1", project.id());
        Assert.assertEquals("p1", removed.id());
        Assert.assertEquals(addGraphs, bodyCaptor.getAllValues().get(0).graphs());
        Assert.assertEquals("add_graph", paramsCaptor.getAllValues().get(0).get("action"));
        Assert.assertEquals(removeGraphs, bodyCaptor.getAllValues().get(1).graphs());
        Assert.assertEquals("remove_graph", paramsCaptor.getAllValues().get(1).get("action"));
    }

    @Test
    public void testManagerRoleMethodsBuildUserManagersAndQueryParams() {
        Mockito.when(this.client.post(Mockito.endsWith("/auth/managers"),
                                      Mockito.any(UserManager.class)))
               .thenReturn(result("{\"user\":\"marko\",\"type\":\"ADMIN\"}"))
               .thenReturn(result("{\"user\":\"josh\","
                                  + "\"type\":\"SPACE\","
                                  + "\"graphspace\":\"DEFAULT\"}"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor =
                ArgumentCaptor.forClass(Map.class);
        Mockito.when(this.client.get(Mockito.endsWith("/auth/managers"),
                                     paramsCaptor.capture()))
               .thenReturn(result("{\"admins\":[\"josh\"]}"));
        Mockito.when(this.client.get(Mockito.endsWith("/auth/managers/check"),
                                     paramsCaptor.capture()))
               .thenReturn(result("{\"check\":true}"));
        Mockito.when(this.client.get(Mockito.endsWith("/auth/managers/default"),
                                     paramsCaptor.capture()))
               .thenReturn(result("{\"check\":false}"));
        ArgumentCaptor<UserManager> bodyCaptor = ArgumentCaptor.forClass(UserManager.class);

        UserManager superAdmin = this.auth.addSuperAdmin("marko");
        UserManager spaceAdmin = this.auth.addSpaceAdmin("josh", "DEFAULT");
        List<String> admins = this.auth.listSpaceAdmin("DEFAULT");
        boolean isSpaceAdmin = this.auth.isSpaceAdmin("DEFAULT");
        boolean defaultRole = this.auth.checkDefaultRole("DEFAULT", "admin", "hugegraph");
        this.auth.delSpaceAdmin("josh", "DEFAULT");

        Mockito.verify(this.client, Mockito.times(2))
               .post(Mockito.endsWith("/auth/managers"), bodyCaptor.capture());
        Assert.assertEquals(HugePermission.ADMIN, bodyCaptor.getAllValues().get(0).type());
        Assert.assertEquals(HugePermission.SPACE, bodyCaptor.getAllValues().get(1).type());
        Assert.assertEquals("marko", superAdmin.user());
        Assert.assertEquals("DEFAULT", spaceAdmin.graphSpace());
        Assert.assertEquals(Collections.singletonList("josh"), admins);
        Assert.assertTrue(isSpaceAdmin);
        Assert.assertFalse(defaultRole);
        Assert.assertEquals(HugePermission.SPACE, paramsCaptor.getAllValues().get(0).get("type"));
        Assert.assertEquals("hugegraph", paramsCaptor.getAllValues().get(2).get("graph"));
        Mockito.verify(this.client).delete(Mockito.endsWith("/auth/managers"),
                                           Mockito.<Map<String, Object>>argThat(params -> {
                                               return "josh".equals(params.get("user")) &&
                                                      HugePermission.SPACE == params.get("type") &&
                                                      "DEFAULT".equals(params.get("graphspace"));
                                           }));
    }

    @Test
    public void testLoginLogoutAndTokenDelegateToGlobalAuthPaths() {
        Login login = new Login();
        login.name("admin");
        login.password("pw");
        Mockito.when(this.client.post(Mockito.eq("auth/login"), Mockito.same(login)))
               .thenReturn(result("{\"token\":\"abc\"}"));
        Mockito.when(this.client.get("auth/verify"))
               .thenReturn(result("{\"user_id\":\"u1\",\"user_name\":\"admin\"}"));

        TokenPayload payload = this.auth.verifyToken();
        String token = this.auth.login(login).token();
        this.auth.logout();

        Assert.assertEquals("u1", payload.userId());
        Assert.assertEquals("abc", token);
        Mockito.verify(this.client).delete(Mockito.eq("auth/logout"), Mockito.anyMap());
    }

    private static RestResult result(String content) {
        return new RestResult(200, content, new RestHeaders());
    }
}
