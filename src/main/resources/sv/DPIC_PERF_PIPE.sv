`include "DPIC_TYPES_DEFINE.sv"
import "DPI-C" function void dpic_perf_pipe(input `uint8_t id, input `uint8_t valid, input `uint8_t stall);
module DPIC_PERF_PIPE #(
    parameter DATA_WIDTH = 32
) (
    input   wire clk,
    input   wire rst,
    input   wire [7:0] id,
    input   wire invalid,
    input   wire stall
);

always @(posedge clk) begin
    if(!rst) begin
        dpic_perf_pipe(id, {7'b0, invalid}, {7'b0, stall});
    end
end
    
endmodule