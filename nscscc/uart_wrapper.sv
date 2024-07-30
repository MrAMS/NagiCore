module uart_wrapper # (
  parameter clk_freq = 50000000,
  parameter uart_baud = 9600
)(
  input wire clk,
  input wire rst,
  output         io_uart_ar_ready,
  output  [7:0]  io_uart_r_id,
  output  [1:0]  io_uart_r_resp,
  output  [31:0] io_uart_r_data,
  output         io_uart_r_last,
  output         io_uart_r_valid,
  output         io_uart_aw_ready,
  output         io_uart_w_ready,
  output  [7:0]  io_uart_b_id,
  output  [1:0]  io_uart_b_resp,
  output         io_uart_b_valid,

  input [7:0]  io_uart_ar_id,
  input [31:0] io_uart_ar_addr,
  input [7:0]  io_uart_ar_len,
  input [2:0]  io_uart_ar_size,
  input [1:0]  io_uart_ar_burst,
  input        io_uart_ar_valid,
  input        io_uart_r_ready,
  input [7:0]  io_uart_aw_id,
  input [31:0] io_uart_aw_addr,
  input [7:0]  io_uart_aw_len,
  input [2:0]  io_uart_aw_size,
  input [1:0]  io_uart_aw_burst,
  input        io_uart_aw_valid,
  input [31:0] io_uart_w_data,
  input [3:0]  io_uart_w_strb,
  input        io_uart_w_last,
  input        io_uart_w_valid,
  input        io_uart_b_ready,


    output wire txd,  //直连串口发送端
    input  wire rxd  //直连串口接收端
);

wire [7:0] ext_uart_rx;
reg  [7:0] ext_uart_tx;
wire ext_uart_ready, ext_uart_clear, ext_uart_busy;
reg ext_uart_start;


reg [7:0] rid;
reg [31:0] raddr;
reg stater;

wire ar_fire = io_uart_ar_valid && io_uart_ar_ready;
wire r_fire = io_uart_r_valid && io_uart_r_ready;
wire [7:0] uart_state = {6'b0, ext_uart_ready, !ext_uart_busy};
// 0xBFD003F8 -> rw data
// 0xBFD003FC -> state
wire read_state = raddr[2];
wire [7:0] rdata = read_state ? uart_state : ext_uart_rx;

assign io_uart_ar_ready = !stater;
assign io_uart_r_valid = (read_state ? 1 : ext_uart_ready) && stater;
assign io_uart_r_data = {4{rdata}};
assign io_uart_r_id = rid;
assign io_uart_r_last = io_uart_r_valid;
assign io_uart_r_resp = 0;

always @(posedge clk) begin
  if (rst) begin
    stater <= 0;
  end else begin
    if(!stater&&ar_fire) begin
      rid <= io_uart_ar_id;
      raddr <= io_uart_ar_addr;
      stater <= 1;
    end
    if(stater&&r_fire) begin
        stater <= 0;
    end
  end
end


async_receiver #(.ClkFrequency(clk_freq),.Baud(uart_baud)) //接收模块，9600无检验位
    ext_uart_r(
        .clk(clk),                       //外部时钟信号
        .RxD(rxd),                           //外部串行信号输入
        .RxD_data_ready(ext_uart_ready),  //数据接收到标志
        .RxD_clear(ext_uart_clear),       //清除接收标志
        .RxD_data(ext_uart_rx)             //接收到的一字节数据
    );

assign ext_uart_clear = r_fire && !read_state;

wire aw_fire = io_uart_aw_valid && io_uart_aw_ready;
wire w_fire = io_uart_w_valid && io_uart_w_ready;
wire b_fire = io_uart_b_valid && io_uart_b_ready;

reg [7:0] wid;
reg wb;

// parameter WS_IDLE = 0;
// parameter WS_W = 1;
// parameter WS_B = 2;

assign io_uart_aw_ready = 1;
assign io_uart_w_ready = !ext_uart_busy;
assign io_uart_b_id = wid;
assign io_uart_b_valid = wb;
assign io_uart_b_resp = 0;


always @(posedge clk) begin
  if (rst) begin
    wb <= 0;
  end else begin
    if(aw_fire) begin
      wid <= io_uart_aw_id;
    end
    if(!wb&&w_fire) begin
        wb <= 1;
    end
    if(wb&&b_fire) begin
        wb <= 0;
    end
  end
end

always @(posedge clk) begin //将缓冲区ext_uart_buffer发送出去
    if(rst) begin
        ext_uart_tx <= 0;
        ext_uart_start <= 0;
    end else begin
      if(!ext_uart_busy&&w_fire)begin 
          ext_uart_tx <= io_uart_w_data[7:0];
          ext_uart_start <= 1;
      end else if(ext_uart_busy) begin 
          ext_uart_start <= 0;
      end
    end
end

async_transmitter #(.ClkFrequency(clk_freq),.Baud(uart_baud)) //发送模块，9600无检验位
    ext_uart_t(
        .clk(clk),                  //外部时钟信号
        .TxD(txd),                      //串行信号输出
        .TxD_busy(ext_uart_busy),       //发送器忙状态指示
        .TxD_start(ext_uart_start),    //开始发送信号
        .TxD_data(ext_uart_tx)        //待发送的数据
    );

endmodule