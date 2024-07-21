BUILD_DIR 	= build
SRC 		= $(shell find src/main/scala -name "*.scala")
SRC			+= $(shell find src/main/resources/sv -name "*.sv" -or -name "*.v")

TARGET 		= $(BUILD_DIR)/Core.v

$(TARGET): $(SRC)
	-rm -rf $(BUILD_DIR)
	mill nagicore.run hello

generate: $(TARGET)

generate-nscscc: $(SRC)
	-rm -rf $(BUILD_DIR)
	mill nagicore.run NSCSCC
	rm -rf ./nscscc/nagicore
	mkdir -p ./nscscc/nagicore
	cp build/*.sv nscscc/nagicore

test: generate
	xmake b diff
	xmake r diff

wave:
	xmake r wave

config:
	xmake f --menu

clean: 
	-rm -rf $(BUILD_DIR)

intellij-init:
	mill mill.idea.GenIdea/idea

.PHONY: intellij-init clean config wave