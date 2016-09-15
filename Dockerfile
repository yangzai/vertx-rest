FROM vertx/vertx3

# Not applicable for Heroku
EXPOSE 8080

ENV USR myuser
ENV WORK_DIR /$USR
ENV VERTICLE_HOME $WORK_DIR/verticles
ENV VERTICLE_NAME HttpServerVerticle

# Copy from IntelliJ build path
COPY /build/classes/main $VERTICLE_HOME

# Run as non-root user
RUN adduser -D $USR
RUN chown -R $USR $WORK_DIR
USER $USR
WORKDIR $WORK_DIR

# We use the "sh -c" to turn around https://github.com/docker/docker/issues/5509 - variable not expanded
#ENTRYPOINT ["sh", "-c"] # already default in Heroku, resetting causes bug
CMD vertx run $VERTICLE_NAME -cp $VERTICLE_HOME