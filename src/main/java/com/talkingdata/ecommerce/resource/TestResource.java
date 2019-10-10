package com.talkingdata.ecommerce.resource;

import com.google.gson.Gson;
import com.talkingdata.ecommerce.entity.TestDemo;
import com.talkingdata.ecommerce.service.TestService;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * @author wwy
 * @date 2019-08-28
 */
@Singleton
@Path("/")
public class TestResource {

    @Inject
    private TestService testService;

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTestDemo(@PathParam("id") Integer id) {
        try {
            TestDemo testDemo = testService.findById(id);
            if (testDemo != null) {
                return Response.ok(new Gson().toJson(testDemo)).build();
            }
            return Response.status(Response.Status.NOT_FOUND).build();
        } catch (Exception e) {
            return null;
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response hello() {
        return Response.ok("hello world!").build();
    }

}
