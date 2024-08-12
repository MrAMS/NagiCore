`include "DPIC_TYPES_DEFINE.sv"
import "DPI-C" function void dpic_update_pc2(input `uint32_t pc1, input `uint8_t valid1, input `uint32_t pc2, input `uint8_t valid2);
module DPIC_UPDATE_PC2 #(
    parameter DATA_WIDTH = 32
) (
    input   wire clk,
    input   wire rst,
    input   wire wen1,
    input   wire [DATA_WIDTH-1:0] pc1,
    input   wire wen2,
    input   wire [DATA_WIDTH-1:0] pc2
);

always @(posedge clk) begin
    if(!rst) begin
        dpic_update_pc2(pc1, {7'b0, wen1}, pc2, {7'b0, wen2});
    end
end
    
endmodule