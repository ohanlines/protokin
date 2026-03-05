FROM clojure:temurin-21-tools-deps

WORKDIR /app

COPY deps.edn ./
RUN clj -P

COPY . .

ENV PORT=8080
EXPOSE 8080

CMD ["clj", "-M", "-m", "protokin.core"]

