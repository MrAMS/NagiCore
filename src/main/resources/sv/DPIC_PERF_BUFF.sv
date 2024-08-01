`include "DPIC_TYPES_DEFINE.sv"
import "DPI-C" function void dpic_update_instrs_buff(input `uint8_t id, input `uint8_t head, input `uint8_t tail, input `uint8_t full, input `uint8_t reload);
module DPIC_PERF_BUFF #(
    parameter DATA_WIDTH = 32
) (
    input   wire clk,
    input   wire rst,
    input   wire [7:0] id,
    input   wire [7:0] head,
    input   wire [7:0] tail,
    input   wire [7:0] full,
    input   wire [7:0] reload
);

always @(posedge clk) begin
    if(!rst) begin
        dpic_update_instrs_buff(id, head, tail, full, reload);
    end
end
    
endmodule